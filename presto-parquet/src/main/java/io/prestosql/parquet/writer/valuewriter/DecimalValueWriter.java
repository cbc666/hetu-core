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
package io.prestosql.parquet.writer.valuewriter;

import io.prestosql.spi.block.Block;
import io.prestosql.spi.type.DecimalType;
import io.prestosql.spi.type.Int128;
import io.prestosql.spi.type.Type;
import org.apache.parquet.column.values.ValuesWriter;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.PrimitiveType;

import java.math.BigInteger;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public class DecimalValueWriter
        extends PrimitiveValueWriter
{
    private final DecimalType decimalType;

    public DecimalValueWriter(ValuesWriter valuesWriter, Type type, PrimitiveType parquetType)
    {
        super(parquetType, valuesWriter);
        this.decimalType = (DecimalType) requireNonNull(type, "type is null");
    }

    @Override
    public void write(Block block)
    {
        if (decimalType.getPrecision() <= 9) {
            for (int i = 0; i < block.getPositionCount(); ++i) {
                if (!block.isNull(i)) {
                    int value = (int) decimalType.getLong(block, i);
                    getValueWriter().writeInteger(value);
                    getStatistics().updateStats(value);
                }
            }
        }
        else if (decimalType.isShort()) {
            for (int i = 0; i < block.getPositionCount(); ++i) {
                if (!block.isNull(i)) {
                    long value = decimalType.getLong(block, i);
                    getValueWriter().writeLong(value);
                    getStatistics().updateStats(value);
                }
            }
        }
        else {
            for (int i = 0; i < block.getPositionCount(); ++i) {
                if (!block.isNull(i)) {
                    Int128 decimal = (Int128) decimalType.getObject(block, i);
                    BigInteger bigInteger = decimal.toBigInteger();
                    Binary binary = Binary.fromConstantByteArray(paddingBigInteger(bigInteger));
                    getValueWriter().writeBytes(binary);
                    getStatistics().updateStats(binary);
                }
            }
        }
    }

    private byte[] paddingBigInteger(BigInteger bigInteger)
    {
        byte[] result = new byte[getTypeLength()];
        if (bigInteger.signum() < 0) {
            Arrays.fill(result, (byte) 0xFF);
        }
        byte[] bytes = bigInteger.toByteArray();
        for (int i = bytes.length - 1, j = result.length - 1; i >= 0; --i, --j) {
            result[j] = bytes[i];
        }
        return result;
    }
}
