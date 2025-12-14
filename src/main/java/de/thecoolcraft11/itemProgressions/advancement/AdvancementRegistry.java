package de.thecoolcraft11.itemProgressions.advancement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;


@SuppressWarnings("deprecation")
public class AdvancementRegistry {
    private final Plugin plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<NamespacedKey, Advancement> registered = new HashMap<>();

    public AdvancementRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(String namespacedKeyString, JsonObject advancementData) {
        NamespacedKey key = parseKey(namespacedKeyString);
        if (key == null) {
            plugin.getLogger().warning("Invalid namespaced key: " + namespacedKeyString);
            return;
        }

        JsonObject normalized = ensureAdvancementStructure(advancementData);
        String json = gson.toJson(normalized);

        Advancement existing = Bukkit.getAdvancement(key);
        if (existing != null) {
            Bukkit.getUnsafe().removeAdvancement(key);
        }

        unregister(key);

        Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
        if (advancement == null) {
            plugin.getLogger().warning("Server rejected advancement " + namespacedKeyString + ")");
            return;
        }

        registered.put(key, advancement);
        plugin.getLogger().info("Registered advancement: " + namespacedKeyString);
    }

    public void registerAll(Map<String, JsonObject> advancements) {
        clearRegistered();
        advancements.forEach(this::register);
        plugin.getLogger().info("Loaded " + registered.size() + " advancements into the runtime registry");
    }

    public boolean unregister(String namespacedKeyString) {
        NamespacedKey key = parseKey(namespacedKeyString);
        if (key == null) {
            return false;
        }
        return unregister(key);
    }

    private boolean unregister(NamespacedKey key) {
        registered.remove(key);
        return Bukkit.getUnsafe().removeAdvancement(key);
    }

    public void clearRegistered() {
        registered.keySet().forEach(key -> Bukkit.getUnsafe().removeAdvancement(key));
        registered.clear();
    }

    private NamespacedKey parseKey(String key) {
        try {
            return NamespacedKey.fromString(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private JsonObject ensureAdvancementStructure(JsonObject advancement) {
        JsonObject result = advancement.deepCopy();

        if (result.has("display")) {
            JsonObject display = result.getAsJsonObject("display");
            if (display != null && display.has("icon")) {
                JsonObject icon = display.getAsJsonObject("icon");
                if (icon != null) {
                    if (icon.has("item") && !icon.has("id")) {
                        String itemId = icon.get("item").getAsString();
                        icon.remove("item");
                        icon.addProperty("id", itemId);
                        display.add("icon", icon);
                        result.add("display", display);
                    }
                }
            }
        }

        if (!result.has("display")) {
            JsonObject display = getJsonObject();
            result.add("display", display);
        } else {
            JsonObject display = result.getAsJsonObject("display");
            if (display != null && !display.has("icon")) {
                JsonObject icon = new JsonObject();
                icon.addProperty("id", "minecraft:book");
                display.add("icon", icon);
                result.add("display", display);
            }
        }

        if (!result.has("criteria")) {
            JsonObject criteria = new JsonObject();
            JsonObject defaultCriterion = new JsonObject();
            defaultCriterion.addProperty("trigger", "minecraft:impossible");
            defaultCriterion.add("conditions", new JsonObject());
            criteria.add("unlock", defaultCriterion);
            result.add("criteria", criteria);
        }

        if (!result.has("requirements")) {
            JsonObject criteria = result.getAsJsonObject("criteria");
            com.google.gson.JsonArray requirements = new com.google.gson.JsonArray();
            for (String name : criteria.keySet()) {
                com.google.gson.JsonArray entry = new com.google.gson.JsonArray();
                entry.add(name);
                requirements.add(entry);
            }
            result.add("requirements", requirements);
        }

        if (!result.has("parent")) {
            if (result.has("display")) {
                JsonObject display = result.getAsJsonObject("display");
                if (display != null && !display.has("background")) {
                    result.addProperty("parent", "minecraft:story/root");
                }
            } else {
                result.addProperty("parent", "minecraft:story/root");
            }
        }

        return result;
    }

    private static @NonNull JsonObject getJsonObject() {
        JsonObject display = new JsonObject();
        JsonObject title = new JsonObject();
        title.addProperty("text", "Advancement");
        JsonObject description = new JsonObject();
        description.addProperty("text", "A custom advancement");
        JsonObject icon = new JsonObject();
        icon.addProperty("id", "minecraft:book");
        display.add("title", title);
        display.add("description", description);
        display.add("icon", icon);
        display.addProperty("frame", "task");
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);
        return display;
    }
}

