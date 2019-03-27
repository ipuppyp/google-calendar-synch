package org.google.calendar.synch;

import org.google.calendar.synch.service.CalendarCrudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Events;

public class CalendarSynchApp {

	private static final Logger LOGGER = LoggerFactory.getLogger(CalendarSynchApp.class);

	private static final String APPLICATION_NAME = "ipuppyp/google-calendar-sync";
	private static final String DATA_STORE_DIR = System.getProperty("user.home")
			+ "/.store/google-calendar-synch.tokens";
	private static final String CREDENTIALS = System.getProperty("user.home")
			+ "/.store/google-calendar-synch-credentials.json";

	private final String targetCalendar;
	private final String eventPrefix;
	private final String sourceCalendar;
	private final String eventFilter;

	CalendarCrudService calendarCrudService;

	public static void main(String[] args) {
		new CalendarSynchApp().doSynch();
	}

	public CalendarSynchApp() {
		calendarCrudService = new CalendarCrudService(APPLICATION_NAME, CREDENTIALS, DATA_STORE_DIR);
		sourceCalendar = getProperty("sourceCalendar");
		targetCalendar = getProperty("targetCalendar");
		eventPrefix = getProperty("eventPrefix");
		eventFilter = getProperty("eventFilter");
	}

	private String getProperty(String key) {
		String property = System.getProperty(key);
		if (property == null) {
			System.err.printf("Missing configuration: %s, please set it with -D\n", key);
			System.exit(-1);
		}
		return property;

	}

	private void doSynch() {
		LOGGER.info("*******************************");
		LOGGER.info("* STARTING SYNCHRONIZATION... *");
		LOGGER.info("*******************************");

		LOGGER.info("*******************************");
		LOGGER.info("* PARAMS:\t*");
		LOGGER.info("* sourceCalendar = {}\t*", sourceCalendar);
		LOGGER.info("* targetCalendar = {}\t*", targetCalendar);
		LOGGER.info("* eventPrefix = {}\t*", eventPrefix);
		LOGGER.info("* eventFilter = {}\t*", eventFilter);
		LOGGER.info("*******************************");

		Calendar source = calendarCrudService.findCalendarByName(sourceCalendar);
		Calendar target = calendarCrudService.findCalendarByName(targetCalendar);
		removeAllEvents(target, eventPrefix);
		//copyAllEvent(source, target, eventPrefix, eventFilter);

		LOGGER.info("******************************");
		LOGGER.info("* SYNCHRONIZATION FINISHED.  *");
		LOGGER.info("******************************");

	}

	private void removeAllEvents(Calendar calendar, String prefix) {
		LOGGER.debug("Removing all events from calendar {}, prefix: {} ...", calendar.getSummary(), prefix);
		long runCount = calendarCrudService.findEventsByCalendar(calendar).getItems().stream()
				.filter(event -> event.getSummary().contains(prefix)).peek(event -> {
					calendarCrudService.removeEvent(calendar, event);
				}).count();
		LOGGER.debug("{} Events removed from {}.", runCount, calendar.getSummary());

	}

	private void copyAllEvent(Calendar sourceCalendar, Calendar targetCalendar, String prefix, String filter) {
		LOGGER.debug("Adding events from calendar {}, prefix: {}, filter: {}...", sourceCalendar.getSummary(), prefix,
				filter);
		Events events = calendarCrudService.findEventsByCalendar(sourceCalendar);
		long runCount = events.getItems().stream()
				.filter(event -> !event.getSummary().toLowerCase().contains(filter.toLowerCase())).peek(event -> {
					calendarCrudService.addEvent(targetCalendar,
							event.setSummary(prefix + " " + event.getSummary()).setId(null).setICalUID(null));
				}).count();
		LOGGER.debug("{} Events added to {}.", runCount, targetCalendar.getSummary());
	}

}
