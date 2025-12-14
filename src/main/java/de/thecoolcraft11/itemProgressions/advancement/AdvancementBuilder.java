package de.thecoolcraft11.itemProgressions.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;


public class AdvancementBuilder {
    private final JsonObject advancement;
    private final JsonObject display;
    private final Set<String> criteriaNames = new LinkedHashSet<>();

    public AdvancementBuilder(String title, String description) {
        advancement = new JsonObject();
        display = new JsonObject();

        display.add("title", textComponent(title));
        display.add("description", textComponent(description));

        JsonObject icon = new JsonObject();
        icon.addProperty("id", "minecraft:book");
        display.add("icon", icon);

        display.addProperty("frame", "task");
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);

        advancement.add("display", display);
    }

    public AdvancementBuilder setParent(String parentNamespacedKey) {
        advancement.addProperty("parent", parentNamespacedKey);
        return this;
    }

    public AdvancementBuilder setIcon(String itemId) {
        JsonObject icon = new JsonObject();
        icon.addProperty("id", itemId);
        display.add("icon", icon);
        return this;
    }

    public AdvancementBuilder setFrameType(String frameType) {
        display.addProperty("frame", frameType);
        return this;
    }

    public AdvancementBuilder setShowToast(boolean showToast) {
        display.addProperty("show_toast", showToast);
        return this;
    }

    public AdvancementBuilder setAnnounceToChat(boolean announce) {
        display.addProperty("announce_to_chat", announce);
        return this;
    }

    public AdvancementBuilder setHidden(boolean hidden) {
        display.addProperty("hidden", hidden);
        return this;
    }

    public AdvancementBuilder addCriteria(String criteriaName, String trigger) {
        if (!advancement.has("criteria")) {
            advancement.add("criteria", new JsonObject());
        }

        JsonObject criteria = advancement.getAsJsonObject("criteria");
        JsonObject criteriaData = new JsonObject();
        criteriaData.addProperty("trigger", trigger);
        JsonObject conditions = new JsonObject();
        criteriaData.add("conditions", conditions);
        criteria.add(criteriaName, criteriaData);
        criteriaNames.add(criteriaName);

        return this;
    }

    public AdvancementBuilder addSimpleCriteria(String criteriaName) {
        return addCriteria(criteriaName, "minecraft:impossible");
    }

    private void ensureRequirements() {
        if (advancement.has("requirements")) {
            return;
        }
        JsonArray requirements = new JsonArray();
        if (criteriaNames.isEmpty()) {
            criteriaNames.add("unlock");
        }
        for (String criteriaName : criteriaNames) {
            JsonArray criteriaEntry = new JsonArray();
            criteriaEntry.add(criteriaName);
            requirements.add(criteriaEntry);
        }
        advancement.add("requirements", requirements);
    }

    public JsonObject build() {
        if (criteriaNames.isEmpty()) {
            addSimpleCriteria("unlock");
        }
        ensureRequirements();
        return advancement;
    }

    private JsonObject textComponent(String text) {
        JsonObject component = new JsonObject();
        component.addProperty("text", text);
        return component;
    }

    public static AdvancementBuilder createTab(String title, String description, String icon, String backgroundTexture) {
        AdvancementBuilder builder = new AdvancementBuilder(title, description);
        if (icon != null && !icon.isEmpty()) {
            builder.setIcon(icon);
        } else {
            builder.setIcon("minecraft:nether_star");
        }
        builder.advancement.remove("parent");
        if (backgroundTexture != null && !backgroundTexture.isEmpty()) {
            builder.display.addProperty("background", backgroundTexture);
        }
        return builder;
    }
}

