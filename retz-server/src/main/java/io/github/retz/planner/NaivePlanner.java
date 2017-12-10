/**
 *    Retz
 *    Copyright (C) 2016-2017 Nautilus Technologies, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.github.retz.planner;

import io.github.retz.cli.TimestampHelper;
import io.github.retz.planner.spi.Resource;
import io.github.retz.protocol.data.Application;
import io.github.retz.protocol.data.Job;
import io.github.retz.scheduler.Applications;
import io.github.retz.scheduler.TaskBuilder;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class NaivePlanner implements Planner {
    private static final Logger LOG = LoggerFactory.getLogger(NaivePlanner.class);
    private static final List<String> ORDER_BY = Arrays.asList("id");

    @Override
    public List<String> orderBy() {
        return ORDER_BY;
    }

    // TODO: very naive packing, from left to right, no searching
    static void pack(List<Protos.Offer> offers,
                     List<AppJobPair> appJobs, // Inputs
                     List<OfferAcceptor> acceptors, // output offers to be accepted per SlaveId
                     List<Job> spill,
                     String unixUser) {
        Map<String, OfferAcceptor> slaveAcceptors = new HashMap<>();
        for (Protos.Offer offer : offers) {
            if (slaveAcceptors.containsKey(offer.getSlaveId().getValue())) {
                slaveAcceptors.get(offer.getSlaveId().getValue()).addOffer(offer);
            } else {
                slaveAcceptors.put(offer.getSlaveId().getValue(), new OfferAcceptor(offer));
            }
        }

        for (Map.Entry<String, OfferAcceptor> e : slaveAcceptors.entrySet()) {
            Resource assigned = new Resource(0, 0, 0);
            Resource resource = e.getValue().totalResource();
            int lastPort = 0;

            while (!appJobs.isEmpty() && 0 <= resource.cpu() && 0 <= resource.memMB()) {
                AppJobPair appJob = appJobs.get(0);
                Job job = appJob.job();

                if (job.resources().getCpu() <= resource.cpu()
                        && job.resources().getMemMB() <= resource.memMB()
                        && job.resources().getGpu() <= resource.gpu()
                        && job.resources().getDiskMB() <= resource.diskMB()
                        && job.resources().getPorts() <= resource.portAmount()) {

                    String id = Integer.toString(job.id());
                    // Not using simple CommandExecutor to keep the executor lifecycle with its assets
                    // (esp ASAKUSA_HOME env)
                    Resource assign = resource.cut(job.resources(), lastPort);
                    lastPort = assign.lastPort();
                    TaskBuilder tb = new TaskBuilder()
                            .setResource(assign, e.getValue().getSlaveID())
                            .setName("retz-" + appJob.application().getAppid() + "-name-" + job.name())
                            .setTaskId("retz-" + appJob.application().getAppid() + "-id-" + id)
                            .setCommand(job, appJob.application(), unixUser);
                    assigned.merge(assign);

                    Protos.TaskInfo task = tb.build();
                    Protos.TaskID taskId = task.getTaskId();
                    job.starting(taskId.getValue(), Optional.empty(), TimestampHelper.now());

                    e.getValue().addTask(task, job);

                    LOG.info("Job {}(task {}) is to be ran as '{}' at Slave {} with resource {}",
                            job.id(), taskId.getValue(), job.cmd(), e.getValue().getSlaveID().getValue(), assign);
                    appJobs.remove(0);
                } else {
                    break;
                }
            }
        }
        acceptors.addAll(slaveAcceptors.values());
        spill.addAll(appJobs.stream().map(appJobPair -> appJobPair.job()).collect(Collectors.toList()));
    }

    private static boolean resourceSufficient(List<Protos.Offer> offers, List<AppJobPair> jobs) {

        int totalCpu = 0, totalMem = 0, totalGPU = 0, totalPorts = 0;
        for (Protos.Offer offer : offers) {
            Resource resource = ResourceConstructor.decode(offer.getResourcesList());
            totalCpu += resource.cpu();
            totalMem += resource.memMB();
            totalGPU += resource.gpu();
            totalPorts += resource.portAmount();
        }

        Optional<Resource> needs = jobs.stream().map(appjob -> new Resource(appjob.job().resources().getCpu(), appjob.job().resources().getMemMB(),
                appjob.job().resources().getDiskMB(), appjob.job().resources().getGpu(), Collections.emptyList())).reduce((lhs, rhs) -> {
            lhs.merge(rhs);
            return lhs;
        });
        int portNeeds = jobs.stream().mapToInt(appJobPair -> appJobPair.job().resources().getPorts()).sum();
        return needs.isPresent()
                && needs.get().cpu() <= totalCpu
                && needs.get().memMB() <= totalMem
                && needs.get().gpu() <= totalGPU
                && portNeeds <= totalPorts;
    }

    @Override
    public List<AppJobPair> filter(List<Job> jobs, List<Job> keep, boolean useGPU) throws IOException {
        // TODO: better splitter
        // Not using GPU or (using GPU and GPU enabled)
        List<Job> run = jobs.stream().filter(job -> job.resources().getGpu() == 0 || useGPU).collect(Collectors.toList());
        keep.addAll(jobs.stream()
                .filter(job -> job.resources().getGpu() > 0 && !useGPU) // Using GPU && GPU not enabled
                .map(job -> {
                    String reason = String.format("Job (%d@%s) requires %d GPUs while this Retz Scheduler is not capable of using GPU resources. Try setting retz.gpu=true at retz.properties.",
                            job.id(), job.appid(), job.resources().getGpu());
                    job.killed(TimestampHelper.now(), Optional.empty(), reason); // Database to be updated later, after plan accepted
                    return job;
                }).collect(Collectors.toList()));

        List<AppJobPair> appJobs = new ArrayList<>(run.size());
        for (Job job : run) {
            Optional<Application> app = Applications.get(job.appid());
            appJobs.add(new AppJobPair(app, job));
        }

        keep.addAll(appJobs.stream()
                .filter(appJobPair -> !appJobPair.hasApplication())
                .map(appJobPair -> {
                    Job job = appJobPair.job();
                    String reason = String.format("Application '%s' of Job (id=%d) does not exist in DB", job.appid(), job.id());
                    job.killed(TimestampHelper.now(), Optional.empty(), reason); // Database to be updated later, after plan accepted
                    return job;
                }).collect(Collectors.toList()));

        return appJobs.stream().filter(appJobPair -> appJobPair.hasApplication()).collect(Collectors.toList());
    }

    // Calculate BEST plan ever, this method apparently must be PURE.
    // INPUT: offers currently this Retz instance has
    // INPUT: jobs - candidates for task launch, most likely chosen from database or else
    // TODO: where to do null-check of unixUser?!
    @Override
    public Plan plan(List<Protos.Offer> offers, List<AppJobPair> jobs, int maxStock, String unixUser) {

        List<OfferAcceptor> acceptors = new ArrayList<>();
        List<Protos.Offer> toStock = new ArrayList<>();

        if (!resourceSufficient(offers, jobs)) {
            for (Protos.Offer offer : offers) {
                if (toStock.size() < maxStock) {
                    toStock.add(offer);
                } else {
                    acceptors.add(new OfferAcceptor(offer));
                }
            }

            return new Plan(acceptors, // OfferAcceptors
                    jobs.stream().map(appJobPair -> appJobPair.job()).collect(Collectors.toList()),
                    toStock); // To be cancelled
        }

        List<Job> keep = new ArrayList<>();

        pack(offers, jobs, acceptors, keep, Objects.requireNonNull(unixUser));

        List<OfferAcceptor> trueAcceptors = new ArrayList<>();
        for (OfferAcceptor acceptor : acceptors) {
            if (acceptor.getJobs().isEmpty() && toStock.size() < maxStock) {
                for (Protos.Offer offer : acceptor.getOffers()) {
                    toStock.add(offer);
                }
            } else {
                trueAcceptors.add(acceptor);
            }
        }

        return new Plan(trueAcceptors, keep, toStock);
    }
}
