package com.kickoffsim.dto;

public record RecapPlayer(String name, String team, int goals, int assists) {

    private static final int INITIALS = 2;

    public String getInitials() {
        if (name == null || name.isBlank()) {
            return "?";
        }
        StringBuilder initials = new StringBuilder();
        for (String part : name.strip().split("\\s+")) {
            if (initials.length() == INITIALS) {
                break;
            }
            initials.append(Character.toUpperCase(part.charAt(0)));
        }
        return initials.toString();
    }
}
