package io.questdb.desktop.ui;

/**
 * Implementors consume events produced by {@link EventProducer}s. An event can be
 * conceptually thought of as a triplet:
 * <ul>
 * <li>source: an implementor of {@link EventProducer}.</li>
 * <li>type: an enum declared by the source, defines the kind of event being consumed.</li>
 * <li>data: an instance of some class (EventDataType) that carries the event data.</li>
 * </ul>
 *
 * @param <EventProducerType> type of the event source
 * @param <EventDataType>     type of the event
 */
@FunctionalInterface
public interface EventConsumer<EventProducerType extends EventProducer<?>, EventDataType> {

    /**
     * Callback.
     *
     * @param source    the source of the event, implements of {@link EventProducer}
     * @param eventType enum declared by the source (kind of event being consumed)
     * @param eventData carries the event data
     */
    void onSourceEvent(EventProducerType source, Enum<?> eventType, EventDataType eventData);
}
