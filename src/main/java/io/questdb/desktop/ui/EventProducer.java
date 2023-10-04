package io.questdb.desktop.ui;

/**
 * Implementors produce events defined by the functional signature declared by {@link EventConsumer}.
 * An event can be conceptually thought of as a triplet:
 * <ul>
 * <li>source: an instance of a class that implements this interface.</li>
 * <li>type: an enum declared by the event producer, defines the kind of event being produced.</li>
 * <li>data: an instance of some class that carries the event data.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public interface EventProducer<EventType extends Enum<?>> {

    @SuppressWarnings("unchecked")
    static <EventType extends Enum<?>> EventType eventType(Enum<?> event) {
        return (EventType) EventType.valueOf(event.getClass(), event.name());
    }
}
