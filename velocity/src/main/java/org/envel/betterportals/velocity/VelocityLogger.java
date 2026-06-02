package org.envel.betterportals.velocity;

import org.envel.betterportals.shared.logging.Logger;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public class VelocityLogger extends Logger {
    private final org.slf4j.Logger logger;

    protected VelocityLogger(org.slf4j.Logger logger) {
        super(logger.getName(), null);
        setLevel(Level.INFO);

        this.logger = logger;
    }

    @Override
    public void log(LogRecord record) {
        // Return if the log level is too low
        if(record.getLevel().intValue() < super.getLevel().intValue()) {return;}

        // Approximately convert between log4j and java.util logging levels
        int recordLevel = record.getLevel().intValue();
        if(recordLevel >= Level.SEVERE.intValue()) {
            logger.error("{}", record.getMessage());
        }   else if(recordLevel >= Level.WARNING.intValue()) {
            logger.warn("{}", record.getMessage());
        }   else if(recordLevel >= Level.INFO.intValue()) {
            logger.info("{}", record.getMessage());
        }   else if(recordLevel >= Level.FINE.intValue()) {
            logger.info("[FNE] {}", record.getMessage());
        }   else if(recordLevel >= Level.FINER.intValue()) {
            logger.info("[FNR] {}", record.getMessage());
        }   else if(recordLevel >= Level.FINEST.intValue()) {
            logger.info("[FST] {}", record.getMessage());
        }
    }
}
