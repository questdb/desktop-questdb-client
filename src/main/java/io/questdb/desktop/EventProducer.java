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

package io.questdb.desktop;

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
