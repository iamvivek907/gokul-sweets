package com.gokulsweets.restaurant.marketing;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.time.*;
import java.util.*;

/** Deterministic, factual playful copy; no invented prices, discounts or stock promises. */
public final class MarketingCopy {
    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /** Approved message and its stable anti-repetition identity. */
    public record Message(String key, String topic, String title, String body) {}

    private static final Map<String, List<String>> TITLES =
            Map.of(
                    "LUNCH",
                            List.of(
                                    "Lunch called. Pick up? 🍽️",
                                    "Lunch plan: less planning 😌",
                                    "Your lunch break has plans 🍽️",
                                    "Meeting with lunch? Accepted ✅"),
                    "SNACKS",
                            List.of(
                                    "Chai needs a plus-one ☕",
                                    "Snack break > screen break 😋",
                                    "A little snack diplomacy? 😉",
                                    "Your break deserves a snack ☕"),
                    "DINNER",
                            List.of(
                                    "Dinner committee: dismissed 😌",
                                    "Dinner plans, minus the debate 🍽️",
                                    "The group chat can rest 😋",
                                    "Dinner has entered the chat 🍽️"),
                    "SWEETS",
                            List.of(
                                    "A sweet little plot twist 😋",
                                    "Dessert has entered the chat 🍬",
                                    "A little mithai diplomacy? 😉",
                                    "Sweet tooth checking in 🍬"));
    private static final Map<String, String> ENGLISH =
            Map.of(
                    "LUNCH", "Explore Gokul meals and choose a pickup time.",
                    "SNACKS", "Explore Gokul snacks. Your chai can thank you later.",
                    "DINNER", "Browse Gokul meals and plan your pickup.",
                    "SWEETS", "Explore Gokul sweets. A little joy goes a long way.");
    private static final Map<String, String> HINGLISH =
            Map.of(
                    "LUNCH", "Lunch ka plan? Gokul ka menu dekho, pickup time chuno.",
                    "SNACKS", "Chai akeli kyun? Gokul ke snacks explore karo.",
                    "DINNER", "Aaj khane mein kya? Gokul ka menu explore karo.",
                    "SWEETS", "Thodi si mithaas banti hai. Gokul ki mithai explore karo.");

    /** Returns meal-time context only during a short India-time promotional window. */
    public static String window(Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingCopy.class, "window(Instant)");
        try {
            int minute = now.atZone(INDIA).getHour() * 60 + now.atZone(INDIA).getMinute();
            if (minute >= 720 && minute < 810) return "LUNCH";
            if (minute >= 960 && minute < 1080) return "SNACKS";
            if (minute >= 1140 && minute < 1230) return "DINNER";
            return null;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MarketingCopy.class, "window(Instant)");
        }
    }

    /**
     * Classifies a historical favourite, without exposing purchased item names on the lock screen.
     */
    public static String preference(String category) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingCopy.class, "preference(String)");
        try {
            String name = category == null ? "" : category.toLowerCase(Locale.ROOT);
            if (name.contains("sweet")) return "SWEETS";
            if (name.contains("snack") || name.contains("breakfast")) return "SNACKS";
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingCopy.class, "preference(String)");
        }
    }

    /** Rotates approved copy deterministically; refuses a repeat inside the supplied history. */
    public static Message choose(
            UUID subject, LocalDate day, String topic, String language, Set<String> recent) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MarketingCopy.class, "choose(UUID,LocalDate,String,String,Set<String>)");
        try {
            var titles = TITLES.get(topic);
            int start = Math.floorMod(Objects.hash(subject, day), titles.size());
            for (int i = 0; i < titles.size(); i++) {
                int variant = (start + i) % titles.size();
                String key = topic + ":" + language + ":" + variant;
                if (!recent.contains(key))
                    return new Message(
                            key,
                            topic,
                            titles.get(variant),
                            ("EN".equals(language) ? ENGLISH : HINGLISH).get(topic));
            }
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MarketingCopy.class,
                    "choose(UUID,LocalDate,String,String,Set<String>)");
        }
    }

    /** Returns all approved preview messages for the selected language. */
    public static List<Message> samples(String language) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingCopy.class, "samples(String)");
        try {
            var result = new ArrayList<Message>();
            for (String topic : List.of("LUNCH", "SNACKS", "DINNER", "SWEETS"))
                for (int i = 0; i < TITLES.get(topic).size(); i++)
                    result.add(
                            new Message(
                                    topic + ":" + language + ":" + i,
                                    topic,
                                    TITLES.get(topic).get(i),
                                    ("EN".equals(language) ? ENGLISH : HINGLISH).get(topic)));
            return List.copyOf(result);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MarketingCopy.class, "samples(String)");
        }
    }
}
