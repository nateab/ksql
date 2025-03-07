/*
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.execution.streams.materialization.ks;

import com.google.common.collect.Streams;
import io.confluent.ksql.GenericKey;
import io.confluent.ksql.GenericRow;
import io.confluent.ksql.execution.streams.materialization.MaterializationException;
import io.confluent.ksql.execution.streams.materialization.MaterializedTable;
import io.confluent.ksql.execution.streams.materialization.Row;
import io.confluent.ksql.util.IteratorUtil;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

/**
 * Kafka Streams impl of {@link MaterializedTable}.
 */
class KsMaterializedTable implements MaterializedTable {

  private final KsStateStore stateStore;

  KsMaterializedTable(final KsStateStore store) {
    this.stateStore = Objects.requireNonNull(store, "store");
  }

  @Override
  public Optional<Row> get(
      final GenericKey key,
      final int partition
  ) {
    try {
      final ReadOnlyKeyValueStore<GenericKey, ValueAndTimestamp<GenericRow>> store = stateStore
          .store(QueryableStoreTypes.timestampedKeyValueStore(), partition);

      return Optional.ofNullable(store.get(key))
          .map(v -> Row.of(stateStore.schema(), key, v.value(), v.timestamp()));
    } catch (final Exception e) {
      throw new MaterializationException("Failed to get value from materialized table", e);
    }
  }

  @Override
  public Iterator<Row> get(final int partition) {
    try {
      final ReadOnlyKeyValueStore<GenericKey, ValueAndTimestamp<GenericRow>> store = stateStore
          .store(QueryableStoreTypes.timestampedKeyValueStore(), partition);

      final KeyValueIterator<GenericKey, ValueAndTimestamp<GenericRow>> iterator = store.all();
      return Streams.stream(IteratorUtil.onComplete(iterator, iterator::close))
          .map(keyValue -> Row.of(stateStore.schema(), keyValue.key, keyValue.value.value(),
                                  keyValue.value.timestamp()))
          .iterator();
    } catch (final Exception e) {
      throw new MaterializationException("Failed to scan materialized table", e);
    }
  }

  @Override
  public Iterator<Row> get(final int partition, final GenericKey from, final GenericKey to) {
    try {
      final ReadOnlyKeyValueStore<GenericKey, ValueAndTimestamp<GenericRow>> store = stateStore
          .store(QueryableStoreTypes.timestampedKeyValueStore(), partition);

      final KeyValueIterator<GenericKey, ValueAndTimestamp<GenericRow>> iterator =
          store.range(from, to);
      return Streams.stream(IteratorUtil.onComplete(iterator, iterator::close))
          .map(keyValue -> Row.of(stateStore.schema(), keyValue.key, keyValue.value.value(),
                                  keyValue.value.timestamp()))
          .iterator();
    } catch (final Exception e) {
      throw new MaterializationException("Failed to range scan materialized table", e);
    }
  }

}