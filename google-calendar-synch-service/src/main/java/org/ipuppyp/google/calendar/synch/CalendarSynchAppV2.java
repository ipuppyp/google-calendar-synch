package org.ipuppyp.google.calendar.synch;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.ipuppyp.google.calendar.synch.service.CalendarCrudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.ExtendedProperties;

public class CalendarSynchAppV2 {

	private static final String PRIVATE = "private";

	private static final String ORIG_I_CAL_UID = "origICalUID";

	private static final Logger LOGGER = LoggerFactory.getLogger(CalendarSynchAppV2.class);

	private static final String APPLICATION_NAME = "ipuppyp/google-calendar-sync";
	private static final String DATA_STORE_DIR = System.getProperty("user.home")
			+ "/.store/google-calendar-synch.tokens";
	private static final String CREDENTIALS = System.getProperty("user.home")
			+ "/.store/google-calendar-synch-credentials.json";

	private final String targetCalendar;
	private final String eventPrefix;
	private final String sourceCalendar;
	private final String eventFilter;
	private final Pattern eventFilterPattern;
	
	private CalendarCrudService calendarCrudService;

	public static void main(String[] args) {
		new CalendarSynchAppV2().doSynch();
	}

	public CalendarSynchAppV2() {
		sourceCalendar = getProperty("sourceCalendar");
		targetCalendar = getProperty("targetCalendar");
		eventPrefix = getProperty("eventPrefix");
		eventFilter = getProperty("eventFilter");
		eventFilterPattern = Pattern.compile(eventFilter);
		
	}

	private String getProperty(String key) {
		String property = System.getProperty(key);
		if (property == null) {
			LOGGER.error("Missing configuration: \"{}\", please set it with -D\n", key);
			System.exit(-1);
		}
		return property;

	}

	private void doSynch() {
		LOGGER.info("*********************************");
		LOGGER.info("* STARTING SYNCHRONIZATION...   *");
		LOGGER.info("*********************************");

		LOGGER.info("*********************************");
		LOGGER.info("* PARAMS:\t\t\t*");
		LOGGER.info("* sourceCalendar = {}\t*", sourceCalendar);
		LOGGER.info("* targetCalendar = {}\t*", targetCalendar);
		LOGGER.info("* eventPrefix = {}\t*", eventPrefix);
		LOGGER.info("* eventFilter = {}\t*", eventFilter);
		LOGGER.info("*********************************");

		try {
			calendarCrudService = new CalendarCrudService(APPLICATION_NAME, CREDENTIALS, DATA_STORE_DIR);		
			synch();
		}
		catch (Exception ex) {
			LOGGER.error("Exception during synchronization", ex);
		}

		LOGGER.info("*********************************");
		LOGGER.info("* SYNCHRONIZATION FINISHED.     *");
		LOGGER.info("*********************************");

	}

	private void synch() {
		
		Calendar source = calendarCrudService.findCalendarByName(sourceCalendar);
		Calendar target = calendarCrudService.findCalendarByName(targetCalendar);
		
		List<Event> eventsInSource = calendarCrudService.findEventsByCalendar(source).getItems().stream()
				.filter(event -> !PRIVATE.equals(event.getVisibility()))
				.filter(event -> !contains(event.getSummary())).map(this::setIds).collect(toList());
		List<Event> eventsInTarget = calendarCrudService.findEventsByCalendar(target).getItems().stream()
				.filter(event -> event.getSummary().contains(eventPrefix)).collect(toList());

		
		List<Event> newEvents = eventsInSource.stream().filter(event -> !contains(eventsInTarget, event)).collect(toList());
		List<Event> deletedEvents = eventsInTarget.stream().filter(event -> !contains(eventsInSource, event)).collect(toList());
		List<Event> changedEvents = eventsInSource.stream().filter(event -> changed(eventsInTarget, event)).collect(toList());
		
		LOGGER.info("*********************************");
		LOGGER.info("* Events to add = {}\t\t*", newEvents.size());
		LOGGER.info("* Events to delete = {}\t\t*", deletedEvents.size());
		LOGGER.info("* Events to update = {}\t\t*", changedEvents.size());
		LOGGER.info("*********************************");
		
		calendarCrudService.addEvents(target, newEvents);
		calendarCrudService.removeEvents(target, deletedEvents);
		calendarCrudService.updateEvents(target, changedEvents);

	}

	private boolean contains(Collection<Event> events, Event event) {
		return findByICalId(events, event).isPresent();
	}
	
	private Optional<Event> findByICalId(Collection<Event> events, Event event) {
		return events.stream().filter(origEvent -> equalsByICalUID(origEvent, event)).findAny();
	}

	private boolean equalsByICalUID(Event event, Event origEvent) {
		return Objects.equals(origEvent.getExtendedProperties().getShared().get(ORIG_I_CAL_UID), event.getExtendedProperties().getShared().get(ORIG_I_CAL_UID));
	}
	
	private boolean changed(Collection<Event> events, Event event) {
		Optional<Event> origEvent = findByICalId(events, event);
		boolean changed = origEvent.isPresent() && !equals(event, origEvent.get());
		if (changed) {
			event.setSequence(origEvent.get().getSequence());
			event.setId(origEvent.get().getId());
		}
		return changed;    
		 
	}
	private boolean equals(Event event, Event origEvent) {
		return Objects.equals(origEvent.getSummary(), event.getSummary()) &&
				Objects.equals(origEvent.getStart(), event.getStart()) &&
				Objects.equals(origEvent.getDescription(), event.getDescription()) &&
				Objects.equals(origEvent.getEnd(), event.getEnd());
	}
	
	
	private boolean contains(String str) {
		return eventFilterPattern.matcher(str).find();
	}
	
	private Event setIds(Event event) {
		Map<String, String> shared = new HashMap<>();
		shared.put(ORIG_I_CAL_UID, event.getICalUID());
		return event
				.setSummary(eventPrefix + " " + event.getSummary())
				.setDescription("see details in original event")
				.setId(null)
				.setExtendedProperties(new ExtendedProperties().setShared(shared))
				.setICalUID(null);
		
	}
}

