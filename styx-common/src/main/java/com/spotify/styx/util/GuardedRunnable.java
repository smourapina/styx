/*-
 * -\-\-
 * Spotify styx
 * --
 * Copyright (C) 2017 Spotify AB
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
package com.spotify.styx.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuardedRunnable {

  private static final Logger LOG = LoggerFactory.getLogger(GuardedRunnable.class);

  private GuardedRunnable() {
  }

  public static Runnable guard(Runnable delegate) {
    return () -> runGuarded(delegate);
  }

  public static void runGuarded(Runnable delegate) {
    try {
      delegate.run();
    } catch (Throwable t) {
      LOG.warn("Guarded runnable threw", t);
    }
  }
}
