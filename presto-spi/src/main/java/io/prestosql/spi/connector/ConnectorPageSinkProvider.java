/*
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
package io.prestosql.spi.connector;

import io.prestosql.spi.PrestoException;

import static io.prestosql.spi.StandardErrorCode.NOT_SUPPORTED;

public interface ConnectorPageSinkProvider
{
    ConnectorPageSink createPageSink(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorOutputTableHandle outputTableHandle);

    ConnectorPageSink createPageSink(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorInsertTableHandle insertTableHandle);

    default ConnectorPageSink createPageSink(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorUpdateTableHandle updateTableHandle)
    {
        throw new PrestoException(NOT_SUPPORTED, "This connector does not support updates");
    }

    default ConnectorPageSink createPageSink(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorDeleteAsInsertTableHandle deleteTableHandle)
    {
        throw new PrestoException(NOT_SUPPORTED, "This connector does not support delete");
    }

    default ConnectorPageSink createPageSink(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorVacuumTableHandle vacuumTableHandle)
    {
        throw new PrestoException(NOT_SUPPORTED, "This connector does not support vacuum");
    }

    default ConnectorPageSink createPageSink(ConnectorTransactionHandle transactionHandle, ConnectorSession session, ConnectorTableExecuteHandle tableExecuteHandle)
    {
        throw new IllegalArgumentException("createPageSink not supported for tableExecuteHandle");
    }
}
