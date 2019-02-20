/*-
 * -\-\-
 * Spotify Styx Common
 * --
 * Copyright (C) 2016 - 2019 Spotify AB
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

import com.typesafe.config.Config;
import java.util.Optional;
import java.util.function.Function;


public class ConfigUtil {

  private ConfigUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Optionally get a configuration value.
   */
  public static <T> Optional<T> get(Config config, Function<String, T> getter, String path) {
    if (!config.hasPath(path)) {
      return Optional.empty();
    }
    return Optional.of(getter.apply(path));
  }
}
