/*
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2018 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.styx.storage;

import com.google.cloud.datastore.Batch;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreReaderWriter;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.Transaction;
import com.google.protobuf.ByteString;
import com.spotify.styx.monitoring.Stats;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

interface InstrumentedTransaction extends
    Transaction,
    InstrumentedDatastoreBatchWriter,
    InstrumentedDatastoreReaderWriter {

  Transaction transaction();

  @Override
  default Entity get(Key key) {
    return InstrumentedDatastoreReaderWriter.super.get(key);
  }

  @Override
  default Iterator<Entity> get(Key... key) {
    return InstrumentedDatastoreReaderWriter.super.get(key);
  }

  @Override
  default List<Entity> fetch(Key... keys) {
    return InstrumentedDatastoreReaderWriter.super.fetch(keys);
  }

  @Override
  default <T> QueryResults<T> run(Query<T> query) {
    return InstrumentedDatastoreReaderWriter.super.run(query);
  }

  @Override
  default void addWithDeferredIdAllocation(FullEntity<?>... entities) {
    InstrumentedDatastoreBatchWriter.super.addWithDeferredIdAllocation(entities);
  }

  @Override
  default Entity add(FullEntity<?> entity) {
    return InstrumentedDatastoreBatchWriter.super.add(entity);
  }

  @Override
  default List<Entity> add(FullEntity<?>... entities) {
    return InstrumentedDatastoreBatchWriter.super.add(entities);
  }

  @Override
  default void update(Entity... entities) {
    InstrumentedDatastoreBatchWriter.super.update(entities);
  }

  @Override
  default void delete(Key... keys) {
    InstrumentedDatastoreBatchWriter.super.delete(keys);
  }

  @Override
  default void putWithDeferredIdAllocation(FullEntity<?>... entities) {
    InstrumentedDatastoreBatchWriter.super.putWithDeferredIdAllocation(entities);
  }

  @Override
  default Entity put(FullEntity<?> entity) {
    return InstrumentedDatastoreBatchWriter.super.put(entity);
  }

  @Override
  default List<Entity> put(FullEntity<?>... entities) {
    return InstrumentedDatastoreBatchWriter.super.put(entities);
  }

  @Override
  default Transaction.Response commit() {
    return transaction().commit();
  }

  @Override
  default void rollback() {
    transaction().rollback();
  }

  @Override
  default boolean isActive() {
    return InstrumentedDatastoreBatchWriter.super.isActive();
  }

  @Override
  default Datastore getDatastore() {
    return InstrumentedDatastoreBatchWriter.super.getDatastore();
  }

  @Override
  default ByteString getTransactionId() {
    return transaction().getTransactionId();
  }

  @Override
  default Batch batch() {
    // Work around DatastoreBatchWriter not being a public interface
    return new Batch() {
      @Override
      public Entity add(FullEntity<?> entity) {
        return transaction().add(entity);
      }

      @Override
      public List<Entity> add(FullEntity<?>... entities) {
        return transaction().add();
      }

      @Override
      public Response submit() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Datastore getDatastore() {
        return transaction().getDatastore();
      }

      @Override
      public void addWithDeferredIdAllocation(FullEntity<?>... entities) {
        transaction().addWithDeferredIdAllocation(entities);
      }

      @Override
      public void update(Entity... entities) {
        transaction().update(entities);
      }

      @Override
      public void delete(Key... keys) {
        transaction().delete(keys);
      }

      @Override
      public void putWithDeferredIdAllocation(FullEntity<?>... entities) {
        transaction().putWithDeferredIdAllocation(entities);
      }

      @Override
      public Entity put(FullEntity<?> entity) {
        return transaction().put(entity);
      }

      @Override
      public List<Entity> put(FullEntity<?>... entities) {
        return transaction().put();
      }

      @Override
      public boolean isActive() {
        return transaction().isActive();
      }
    };
  }

  @Override
  default DatastoreReaderWriter readerWriter() {
    return transaction();
  }

  static InstrumentedTransaction of(Stats stats, Transaction transaction) {
    Objects.requireNonNull(stats, "stats");
    Objects.requireNonNull(transaction, "transaction");
    return new InstrumentedTransaction() {
      @Override
      public Transaction transaction() {
        return transaction;
      }

      @Override
      public Stats stats() {
        return stats;
      }
    };
  }
}
