/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.fs;

import org.agrona.DirectBuffer;
import io.zeebe.logstreams.impl.LogStreamImpl;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.spi.SnapshotStorage;

import java.io.File;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class FsLogStreamBuilder extends LogStreamImpl.LogStreamBuilder<FsLogStreamBuilder>
{
    public FsLogStreamBuilder(final DirectBuffer topicName, final int partitionId)
    {
        super(topicName, partitionId);
    }

    @Override
    protected void initLogStorage()
    {
        final String logDirectory = getLogDirectory();

        final File file = new File(logDirectory);
        file.mkdirs();

        final FsLogStorageConfiguration storageConfig = new FsLogStorageConfiguration(logSegmentSize,
            logDirectory,
            initialLogSegmentId,
            deleteOnClose);

        final String topicNameString = topicName.getStringWithoutLengthUtf8(0, topicName.capacity());

        logStorage = new FsLogStorage(storageConfig, actorScheduler.getMetricsManager(), topicNameString, partitionId);
        logStorage.open();
    }

    @Override
    public SnapshotStorage getSnapshotStorage()
    {
        if (snapshotStorage == null)
        {
            snapshotStorage = new FsSnapshotStorageBuilder(logDirectory).build();
        }
        return snapshotStorage;
    }
}
