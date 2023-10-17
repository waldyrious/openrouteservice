package org.heigit.ors.api.services;

import org.apache.log4j.Logger;
import org.heigit.ors.api.Application;
import org.heigit.ors.api.util.AppConfigMigration;
import org.heigit.ors.routing.graphhopper.extensions.manage.ORSGraphManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GraphService {
    private static final Logger LOGGER = Logger.getLogger(AppConfigMigration.class.getName());

    public List<ORSGraphManager> graphManagers = new ArrayList<>();

    public void addGraphhopperLocation(ORSGraphManager orsGraphManager) {
        graphManagers.add(orsGraphManager);
    }

    @Async
    @Scheduled(cron = "${ors.engine.graphservice.schedule.download.cron:0 0 0 31 2 *}")//Default is "never"
    public void checkForUpdatesInRepo() {
        LOGGER.debug("Scheduled check for updates in graph repository...");
        for (ORSGraphManager orsGraphManager : graphManagers) {
            if (orsGraphManager.isActive()) {
                LOGGER.info("[%s] Scheduled check for updates in graph repository: Download or extraction in progress".formatted(orsGraphManager.getRouteProfileName()));
            } else if (orsGraphManager.hasDownloadedExtractedGraph()) {
                LOGGER.info("[%s] A newer graph was already downloaded and extracted".formatted(orsGraphManager.getRouteProfileName()));
            } else {
                orsGraphManager.downloadAndExtractLatestGraphIfNecessary();
            }
        }
        LOGGER.debug("Scheduled check for updates in graph repository done");
    }

    @Async
    @Scheduled(cron = "${ors.engine.graphservice.schedule.activate.cron:0 0 0 31 2 *}")//Default is "never"
    public void checkForDownloadedGraphsToActivate() {
        LOGGER.debug("Scheduled check for downloaded graphs...");
        boolean restartNeeded = false;
        boolean restartAllowed = true;
        for (ORSGraphManager orsGraphManager : graphManagers) {
            if (orsGraphManager.isActive()) {
                LOGGER.info("[%s] Scheduled check for downloaded graphs: Download in progress".formatted(orsGraphManager.getRouteProfileName()));
                restartAllowed = false;
            }
            if (orsGraphManager.hasDownloadedExtractedGraph()) {
                LOGGER.info("[%s] Scheduled check for downloaded graphs: Downloaded extracted graph available".formatted(orsGraphManager.getRouteProfileName()));
                restartNeeded = true;
            }
        }
        if (restartNeeded && restartAllowed) {
            restartApplication();
        } else {
            LOGGER.info("Scheduled check for downloaded graphs done -> restarting openrouteservice is %s".formatted(
                    !restartNeeded ? "not needed" : restartAllowed ? "needed and allowed" : "needed but not allowed (one or more graph managers are active)")
            );
        }
    }

    private void restartApplication() {
        Application.restart();
    }
}
