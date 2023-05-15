/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.quest;

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
