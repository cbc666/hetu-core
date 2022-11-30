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
package io.hetu.core.plugin.iceberg;

import io.prestosql.spi.connector.SchemaTableName;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.MockitoAnnotations.initMocks;

public class UnknownTableTypeExceptionTest
{
    @Mock
    private SchemaTableName mockTableName;

    private UnknownTableTypeException unknownTableTypeExceptionUnderTest;

    @BeforeMethod
    public void setUp() throws Exception
    {
        initMocks(this);
        unknownTableTypeExceptionUnderTest = new UnknownTableTypeException(mockTableName);
    }

    @Test
    public void testGetTableName()
    {
        new UnknownTableTypeException(mockTableName);
        // Run the test
        unknownTableTypeExceptionUnderTest.getTableName();

        // Verify the results
    }
}