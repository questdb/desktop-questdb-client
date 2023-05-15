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

import io.quest.conns.Conn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;


public class ConnectWithQuestDB {
    public static void main(String... args) throws Exception {
        try (Conn conn = new Conn("QuestDB")) {
            Connection connection = conn.open();
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS testing(test TIMESTAMP) TIMESTAMP(test);").execute();
            // Insert timestamp
            Timestamp timestamp = Timestamp.valueOf("2021-09-11 07:29:59.306");
            System.out.printf("Insert: %s%n", timestamp);
            PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO testing VALUES (?);");
            insertStatement.setTimestamp(1, timestamp);
            insertStatement.execute();
            // Get timestamp again
            ResultSet queryResult = connection.prepareStatement("SELECT * FROM testing;").executeQuery();
            queryResult.next();
            Timestamp afterTimestamp = queryResult.getTimestamp("test");
            System.out.printf("Select: %s%n", afterTimestamp);
        }
    }
}
