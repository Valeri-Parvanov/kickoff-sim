package com.kickoffsim.web;

import java.util.Optional;
import java.util.UUID;

public final class LogoGenerator {

    private LogoGenerator() {}

    private static final String[] OUTER_PATHS = {
        "M40 3 L77 20 L77 54 Q77 84 40 89 Q3 84 3 54 L3 20 Z",
        "M40 2 L76 24 L76 60 L40 88 L4 60 L4 24 Z",
        "M40 4 L74 18 L74 52 Q74 80 40 88 Q6 80 6 52 L6 18 Z",
        "M8 24 Q8 4 40 4 Q72 4 72 24 L72 62 L40 86 L8 62 Z",
        "M6 5 L74 5 L74 46 Q74 82 40 89 Q6 82 6 46 Z",
        "M16 4 L64 4 L78 28 L78 58 L40 89 L2 58 L2 28 Z",
        "M6 36 Q6 2 40 2 Q74 2 74 36 L74 58 L40 89 L6 58 Z",
        "M40 2 L74 34 L74 56 L40 89 L6 56 L6 34 Z"
    };

    private static final String[] INNER_PATHS = {
        "M40 8 L71 23 L71 52 Q71 78 40 84 Q9 78 9 52 L9 23 Z",
        "M40 8 L70 26 L70 57 L40 82 L10 57 L10 26 Z",
        "M40 9 L69 21 L69 50 Q69 74 40 81 Q11 74 11 50 L11 21 Z",
        "M13 25 Q13 9 40 9 Q67 9 67 25 L67 58 L40 79 L13 58 Z",
        "M11 10 L69 10 L69 44 Q69 76 40 82 Q11 76 11 44 Z",
        "M20 10 L60 10 L71 30 L71 55 L40 81 L9 55 L9 30 Z",
        "M11 34 Q11 8 40 8 Q69 8 69 34 L69 55 L40 82 L11 55 Z",
        "M40 9 L68 35 L68 54 L40 81 L12 54 L12 35 Z"
    };

    private static final int SHAPE_COUNT = OUTER_PATHS.length + 1;
    private static final int CIRCLE_SHAPE = OUTER_PATHS.length;
    private static final int EMBLEM_COUNT = 8;

    private static final String[] LEAGUE_OUTER_PATHS = {
        "M40 9 L72 27 L72 65 L40 83 L8 65 L8 27 Z",
        "M23 9 L57 9 L77 29 L77 63 L57 83 L23 83 L3 63 L3 29 Z",
        "M18 9 Q3 9 3 24 L3 68 Q3 83 18 83 L62 83 Q77 83 77 68 L77 24 Q77 9 62 9 Z",
        "M40 9 L74 33 L61 83 L19 83 L6 33 Z"
    };

    private static final String[] LEAGUE_INNER_PATHS = {
        "M40 15 L66 30 L66 62 L40 77 L14 62 L14 30 Z",
        "M26 15 L54 15 L71 32 L71 60 L54 77 L26 77 L9 60 L9 32 Z",
        "M20 15 Q9 15 9 26 L9 66 Q9 77 20 77 L60 77 Q71 77 71 66 L71 26 Q71 15 60 15 Z",
        "M40 16 L67 34 L57 77 L23 77 L13 34 Z"
    };

    private static final int LEAGUE_SHAPE_COUNT = LEAGUE_OUTER_PATHS.length + 1;
    private static final int LEAGUE_CIRCLE_SHAPE = LEAGUE_OUTER_PATHS.length;

    public static String generate(String name, UUID id) {
        long pos = id.getMostSignificantBits() & Long.MAX_VALUE;
        long posLo = id.getLeastSignificantBits() & Long.MAX_VALUE;

        int shapeIdx = (int) (pos % SHAPE_COUNT);
        boolean rimGold = ((pos >> 3) & 1) == 0;
        boolean splitRight = ((posLo >> 2) & 1) == 0;
        int emblemIdx = (int) ((pos ^ posLo) % EMBLEM_COUNT);
        int hue = (id.hashCode() & 0x7FFFFFFF) % 360;
        int accentHue = (hue + 140) % 360;

        String uid = Long.toHexString(pos & 0xFFFFFFFL);
        String baseGradId = "b" + uid;
        String accentGradId = "a" + uid;
        String rimGradId = "r" + uid;
        String clipId = "c" + uid;
        String emblemGradId = "e" + uid;
        String shadowId = "s" + uid;

        String svg = "<svg viewBox=\"0 0 80 90\" xmlns=\"http://www.w3.org/2000/svg\">";
        svg += "<defs>";
        svg += linearGradient(baseGradId, "hsl(" + hue + ",60%,30%)", "hsl(" + hue + ",70%,48%)");
        svg += linearGradient(accentGradId, "hsl(" + accentHue + ",65%,42%)", "hsl(" + accentHue + ",75%,58%)");
        svg += linearGradient(emblemGradId, "#ffffff", "#d6d9e0");
        svg += rimGold ? goldGradient(rimGradId) : silverGradient(rimGradId);
        svg += "<clipPath id=\"" + clipId + "\">" + innerClip(shapeIdx) + "</clipPath>";
        svg += dropShadowFilter(shadowId);
        svg += "</defs>";
        svg += "<g filter=\"url(#" + shadowId + ")\">";
        svg += outerShape(shapeIdx, rimGradId);
        svg += innerFill(shapeIdx, baseGradId);
        svg += "<g clip-path=\"url(#" + clipId + ")\">"
                + splitPolygon(splitRight, accentGradId)
                + "</g>";
        svg += highlightArc(shapeIdx);
        svg += buildEmblem(emblemIdx, name, emblemGradId);
        svg += "</g>";
        svg += "</svg>";
        return svg;
    }

    public static String generateLeagueLogo(String name, UUID id) {
        long pos = id.getMostSignificantBits() & Long.MAX_VALUE;
        boolean rimGold = ((pos >> 3) & 1) == 0;
        boolean useMonogram = ((pos >> 5) & 1) == 0;
        int shapeIdx = (int) (pos % LEAGUE_SHAPE_COUNT);
        int hue = (id.hashCode() & 0x7FFFFFFF) % 360;

        String uid = Long.toHexString(pos & 0xFFFFFFFL);
        String baseGradId = "lb" + uid;
        String rimGradId = "lr" + uid;
        String emblemGradId = "le" + uid;
        String shadowId = "ls" + uid;

        Optional<LeagueLogoThemes.Theme> theme = LeagueLogoThemes.match(name);
        String goldGradId = "lg" + uid;
        boolean spill = theme.map(LeagueLogoThemes.Theme::hasOverflow).orElse(false) && ((pos >> 9) & 1) == 0;
        boolean needsTopper = theme.isPresent() && !spill;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 80 90\" xmlns=\"http://www.w3.org/2000/svg\">");
        svg.append("<defs>");
        svg.append(theme.map(t -> linearGradient(baseGradId, t.baseTo(), t.baseFrom()))
                .orElseGet(() -> linearGradient(baseGradId, "hsl(" + hue + ",55%,32%)", "hsl(" + hue + ",65%,50%)")));
        svg.append(linearGradient(emblemGradId, "#ffffff", "#d6d9e0"));
        svg.append(rimGold ? goldGradient(rimGradId) : silverGradient(rimGradId));
        if (needsTopper) {
            svg.append(goldGradient(goldGradId));
        }
        svg.append(dropShadowFilter(shadowId));
        svg.append("</defs>");
        svg.append("<g filter=\"url(#").append(shadowId).append(")\">");
        svg.append(leagueOuterShape(shapeIdx, rimGradId));
        svg.append(leagueInnerFill(shapeIdx, baseGradId));
        if (theme.isPresent()) {
            svg.append(theme.get().motif());
            if (spill) {
                svg.append(theme.get().overflow());
            } else {
                svg.append(LeagueLogoThemes.topper((int) ((pos >> 7) % LeagueLogoThemes.topperCount()), goldGradId));
            }
        } else {
            svg.append("<path d=\"M18 26 Q40 16 62 26\" stroke=\"rgba(255,255,255,0.35)\" stroke-width=\"3\" fill=\"none\" stroke-linecap=\"round\"/>");
            svg.append(useMonogram ? monogramText(name, emblemGradId) : starMark(emblemGradId));
        }
        svg.append("</g>");
        svg.append("</svg>");
        return svg.toString();
    }

    private static String leagueOuterShape(int shapeIdx, String rimGradId) {
        if (shapeIdx == LEAGUE_CIRCLE_SHAPE) {
            return "<circle cx=\"40\" cy=\"46\" r=\"37\" fill=\"url(#" + rimGradId + ")\"/>";
        }
        return "<path d=\"" + LEAGUE_OUTER_PATHS[shapeIdx] + "\" fill=\"url(#" + rimGradId + ")\"/>";
    }

    private static String leagueInnerFill(int shapeIdx, String baseGradId) {
        if (shapeIdx == LEAGUE_CIRCLE_SHAPE) {
            return "<circle cx=\"40\" cy=\"46\" r=\"31\" fill=\"url(#" + baseGradId + ")\"/>";
        }
        return "<path d=\"" + LEAGUE_INNER_PATHS[shapeIdx] + "\" fill=\"url(#" + baseGradId + ")\"/>";
    }

    private static String outerShape(int shapeIdx, String rimGradId) {
        if (shapeIdx == CIRCLE_SHAPE) {
            return "<circle cx=\"40\" cy=\"46\" r=\"37\" fill=\"url(#" + rimGradId + ")\"/>";
        }
        return "<path d=\"" + OUTER_PATHS[shapeIdx] + "\" fill=\"url(#" + rimGradId + ")\"/>";
    }

    private static String innerFill(int shapeIdx, String baseGradId) {
        if (shapeIdx == CIRCLE_SHAPE) {
            return "<circle cx=\"40\" cy=\"46\" r=\"32\" fill=\"url(#" + baseGradId + ")\"/>";
        }
        return "<path d=\"" + INNER_PATHS[shapeIdx] + "\" fill=\"url(#" + baseGradId + ")\"/>";
    }

    private static String innerClip(int shapeIdx) {
        if (shapeIdx == CIRCLE_SHAPE) {
            return "<circle cx=\"40\" cy=\"46\" r=\"32\"/>";
        }
        return "<path d=\"" + INNER_PATHS[shapeIdx] + "\"/>";
    }

    private static String highlightArc(int shapeIdx) {
        int topY = shapeIdx == CIRCLE_SHAPE ? 18 : 16;
        return "<path d=\"M20 " + topY + " Q40 " + (topY - 8) + " 60 " + topY + "\""
                + " stroke=\"rgba(255,255,255,0.35)\" stroke-width=\"3\" fill=\"none\" stroke-linecap=\"round\"/>";
    }

    private static String splitPolygon(boolean splitRight, String accentGradId) {
        String points = splitRight ? "0,0 80,0 80,50 0,20" : "0,0 80,0 80,20 0,50";
        return "<polygon points=\"" + points + "\" fill=\"url(#" + accentGradId + ")\"/>";
    }

    private static String buildEmblem(int idx, String name, String emblemGradId) {
        String c = "url(#" + emblemGradId + ")";
        return switch (idx) {
            case 0 -> "<polygon points=\"40,26 45,40 60,40 48,49 53,64 40,55 27,64 32,49 20,40 35,40\" fill=\"" + c + "\"/>";
            case 1 -> "<polygon points=\"44,22 30,50 39,50 34,72 56,42 46,42\" fill=\"" + c + "\"/>";
            case 2 -> "<path d=\"M40,48 C28,38 16,40 10,32 C14,46 22,54 34,56 Z M40,48 C52,38 64,40 70,32 C66,46 58,54 46,56 Z\" fill=\"" + c + "\"/>";
            case 3 -> "<polygon points=\"40,26 56,48 40,70 24,48\" fill=\"none\" stroke=\"" + c + "\" stroke-width=\"4\"/>";
            case 4 -> "<g stroke=\"" + c + "\" stroke-width=\"6\" stroke-linecap=\"round\">"
                    + "<line x1=\"24\" y1=\"30\" x2=\"56\" y2=\"66\"/>"
                    + "<line x1=\"56\" y1=\"30\" x2=\"24\" y2=\"66\"/></g>";
            case 5 -> "<polygon points=\"40,26 56,35 56,55 40,64 24,55 24,35\" fill=\"none\" stroke=\"" + c + "\" stroke-width=\"3.5\"/>"
                    + "<circle cx=\"40\" cy=\"45\" r=\"5\" fill=\"" + c + "\"/>";
            case 6 -> "<g stroke=\"" + c + "\" stroke-width=\"4\" stroke-linecap=\"round\">"
                    + "<line x1=\"22\" y1=\"28\" x2=\"34\" y2=\"64\"/>"
                    + "<line x1=\"33\" y1=\"26\" x2=\"45\" y2=\"64\"/>"
                    + "<line x1=\"44\" y1=\"28\" x2=\"56\" y2=\"64\"/></g>";
            default -> monogramText(name, emblemGradId);
        };
    }

    private static String monogramText(String name, String emblemGradId) {
        return "<text x=\"40\" y=\"56\" font-family=\"'Poppins', sans-serif\" font-size=\"28\" font-weight=\"800\""
                + " fill=\"url(#" + emblemGradId + ")\" text-anchor=\"middle\">" + escapeXml(initials(name)) + "</text>";
    }

    private static String starMark(String emblemGradId) {
        return "<polygon points=\"40,24 45,38 60,38 48,47 53,62 40,53 27,62 32,47 20,38 35,38\" fill=\"url(#" + emblemGradId + ")\"/>";
    }

    private static String linearGradient(String id, String from, String to) {
        return "<linearGradient id=\"" + id + "\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">"
                + "<stop offset=\"0\" stop-color=\"" + from + "\"/>"
                + "<stop offset=\"1\" stop-color=\"" + to + "\"/></linearGradient>";
    }

    private static String goldGradient(String id) {
        return linearGradient(id, "#f4d27a", "#b8842c");
    }

    private static String silverGradient(String id) {
        return linearGradient(id, "#e8e8ea", "#8d8f96");
    }

    private static String dropShadowFilter(String id) {
        return "<filter id=\"" + id + "\" x=\"-30%\" y=\"-30%\" width=\"160%\" height=\"160%\">"
                + "<feDropShadow dx=\"0\" dy=\"2\" stdDeviation=\"2\" flood-color=\"#000\" flood-opacity=\"0.35\"/></filter>";
    }

    private static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (sb.length() >= 2) break;
        }
        if (sb.length() == 1 && words[0].length() > 1) {
            sb.append(Character.toUpperCase(words[0].charAt(1)));
        }
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
