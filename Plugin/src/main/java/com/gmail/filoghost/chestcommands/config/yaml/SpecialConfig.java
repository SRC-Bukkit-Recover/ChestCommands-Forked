/*
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.gmail.filoghost.chestcommands.config.yaml;

import com.gmail.filoghost.chestcommands.ChestCommands;
import com.gmail.filoghost.chestcommands.util.FormatUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;

/**
 * A special configuration wrapper that reads the values using reflection. It will also save default
 * values if not set.
 */
public class SpecialConfig {

  private final PluginConfig config;
  private String header;
  private Map<String, Object> defaultValuesMap;

  public SpecialConfig(PluginConfig config) {
    this.config = config;
  }

  public void setHeader(String header) {
    this.header = header;
  }

  public void load() throws IOException, InvalidConfigurationException, IllegalAccessException {

    // Check if the configuration was initialized
    if (defaultValuesMap == null) {
      defaultValuesMap = new HashMap<>();

      // Put the values in the default values map
      for (Field field : getClass().getDeclaredFields()) {
        if (!isValidField(field)) {
          continue;
        }

        field.setAccessible(true);
        String configKey = formatFieldName(field);

        try {
          Object defaultValue = field.get(this);
          if (defaultValue != null) {
            defaultValuesMap.put(configKey, defaultValue);
          } else {
            config.getPlugin().getLogger().warning("The field " + field.getName()
                + " was not provided with a default value, please inform the developer.");
          }

        } catch (Exception ex) {
          ChestCommands.getInstance().getLogger()
              .log(Level.WARNING, "Unexpected error when loading field", ex);
        }
      }
    }

    // First of all, try to load the yaml file
    config.load();

    // Save default values not set
    boolean needsSave = false;
    for (Entry<String, Object> entry : defaultValuesMap.entrySet()) {
      if (!config.isSet(entry.getKey())) {
        needsSave = true;
        config.set(entry.getKey(), entry.getValue());
      }
    }

    if (needsSave) {
      config.options().header(header);
      config.save();
    }

    // Now read change the fields
    for (Field field : getClass().getDeclaredFields()) {

      if (!isValidField(field)) {
        continue;
      }

      field.setAccessible(true);
      String configKey = formatFieldName(field);

      if (config.isSet(configKey)) {

        Class<?> type = field.getType();

        if (type == boolean.class || type == Boolean.class) {
          field.set(this, config.getBoolean(configKey));

        } else if (type == int.class || type == Integer.class) {
          field.set(this, config.getInt(configKey));

        } else if (type == double.class || type == Double.class) {
          field.set(this, config.getDouble(configKey));

        } else if (type == String.class) {
          field.set(this, FormatUtils.addColors(config.getString(configKey))); // Always add colors

        } else {
          config.getPlugin().getLogger().warning(
              "Unknown field type: " + field.getType().getName() + " (" + field.getName()
                  + "). Please inform the developer.");
        }

      } else {
        field.set(this, defaultValuesMap.get(configKey));
      }
    }
  }


  private boolean isValidField(Field field) {
    int modifiers = field.getModifiers();
    return !Modifier.isTransient(modifiers) && !Modifier.isStatic(modifiers) && !Modifier
        .isFinal(modifiers);
  }

  private String formatFieldName(Field field) {
    return field.getName().replace("__", ".").replace("_", "-");
  }

}
