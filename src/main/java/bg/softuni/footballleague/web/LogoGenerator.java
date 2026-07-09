package bg.softuni.footballleague.web;

import java.util.UUID;

public final class LogoGenerator {

    private LogoGenerator() {}

    private static final String[] SHIELD_PATHS = {
        "M40 5 L76 20 L76 54 Q76 83 40 88 Q4 83 4 54 L4 20 Z",
        "M40 6 Q74 6 74 28 L74 56 Q74 84 40 88 Q6 84 6 56 L6 28 Q6 6 40 6 Z",
        "M40 4 L76 23 L76 66 L40 86 L4 66 L4 23 Z",
        "M8 25 Q8 5 40 5 Q72 5 72 25 L72 65 L40 84 L8 65 Z",
        "M40 4 L77 28 L63 82 L17 82 L3 28 Z",
        null
    };

    public static String generate(String name, UUID id) {
        long hi = id.getMostSignificantBits();
        long lo = id.getLeastSignificantBits();

        int shapeIdx   = (int) ((hi & Long.MAX_VALUE)        % SHIELD_PATHS.length);
        int patternIdx = (int) ((lo & Long.MAX_VALUE)        % 6);
        int emblemIdx  = (int) (((hi ^ lo) & Long.MAX_VALUE) % 6);
        int hue        = (id.hashCode() & 0x7FFFFFFF)        % 360;
        int accentHue  = (hue + 150) % 360;

        String base   = "hsl(" + hue       + ",62%,27%)";
        String accent = "hsl(" + accentHue + ",68%,42%)";
        String clipId = "s" + Long.toHexString(hi & 0x0FFFFFFFL);

        String shapeEl, outlineEl, innerEl;
        if (shapeIdx == 5) {
            shapeEl   = "<circle cx=\"40\" cy=\"46\" r=\"37\"/>";
            outlineEl = "<circle cx=\"40\" cy=\"46\" r=\"37\" fill=\"none\" stroke=\"rgba(0,0,0,0.28)\" stroke-width=\"2\"/>";
            innerEl   = "<circle cx=\"40\" cy=\"46\" r=\"37\" fill=\"none\" stroke=\"rgba(255,255,255,0.18)\" stroke-width=\"5\"/>";
        } else {
            String d  = SHIELD_PATHS[shapeIdx];
            shapeEl   = "<path d=\"" + d + "\"/>";
            outlineEl = "<path d=\"" + d + "\" fill=\"none\" stroke=\"rgba(0,0,0,0.28)\" stroke-width=\"2\"/>";
            innerEl   = "<path d=\"" + d + "\" fill=\"none\" stroke=\"rgba(255,255,255,0.18)\" stroke-width=\"5\"/>";
        }

        return "<svg viewBox=\"0 0 80 90\" xmlns=\"http://www.w3.org/2000/svg\">" +
               "<defs><clipPath id=\"" + clipId + "\">" + shapeEl + "</clipPath></defs>" +
               "<g clip-path=\"url(#" + clipId + ")\">" +
               "<rect width=\"80\" height=\"90\" fill=\"" + base + "\"/>" +
               buildPattern(patternIdx, accent) +
               innerEl +
               buildEmblem(emblemIdx) +
               "</g>" +
               outlineEl +
               "</svg>";
    }

    private static String buildPattern(int idx, String accent) {
        String w = "rgba(255,255,255,0.35)";
        return switch (idx) {
            case 0 ->
                "<rect x=\"40\" y=\"0\" width=\"40\" height=\"90\" fill=\"" + accent + "\"/>" +
                "<line x1=\"40\" y1=\"0\" x2=\"40\" y2=\"90\" stroke=\"" + w + "\" stroke-width=\"1.5\"/>";
            case 1 ->
                "<rect x=\"0\" y=\"0\" width=\"80\" height=\"45\" fill=\"" + accent + "\"/>" +
                "<line x1=\"0\" y1=\"45\" x2=\"80\" y2=\"45\" stroke=\"" + w + "\" stroke-width=\"1.5\"/>";
            case 2 ->
                "<polygon points=\"0,0 55,0 80,45 80,90 25,90 0,45\" fill=\"" + accent + "\"/>";
            case 3 ->
                "<rect x=\"40\" y=\"0\" width=\"40\" height=\"45\" fill=\"" + accent + "\"/>" +
                "<rect x=\"0\" y=\"45\" width=\"40\" height=\"45\" fill=\"" + accent + "\"/>" +
                "<line x1=\"40\" y1=\"0\" x2=\"40\" y2=\"90\" stroke=\"" + w + "\" stroke-width=\"1\" opacity=\"0.6\"/>" +
                "<line x1=\"0\" y1=\"45\" x2=\"80\" y2=\"45\" stroke=\"" + w + "\" stroke-width=\"1\" opacity=\"0.6\"/>";
            case 4 ->
                "<rect x=\"0\" y=\"0\" width=\"27\" height=\"90\" fill=\"" + accent + "\"/>" +
                "<rect x=\"53\" y=\"0\" width=\"27\" height=\"90\" fill=\"" + accent + "\"/>";
            default ->
                "<rect x=\"0\" y=\"0\" width=\"80\" height=\"30\" fill=\"" + accent + "\"/>" +
                "<rect x=\"0\" y=\"60\" width=\"80\" height=\"30\" fill=\"" + accent + "\"/>";
        };
    }

    private static String buildEmblem(int idx) {
        String c = "rgba(255,255,255,0.88)";
        return switch (idx) {
            case 0 ->
                "<polygon points=\"40,31 44.1,41.3 55.2,42.1 46.7,49.2 49.4,59.9 40,54 30.6,59.9 33.3,49.2 24.8,42.1 35.9,41.3\"" +
                " fill=\"" + c + "\"/>";
            case 1 ->
                "<polygon points=\"40,29 57,47 40,65 23,47\" fill=\"none\" stroke=\"" + c + "\" stroke-width=\"2.5\"/>";
            case 2 ->
                "<polygon points=\"19,62 40,37 61,62 55,62 40,44 25,62\" fill=\"" + c + "\"/>";
            case 3 ->
                "<circle cx=\"40\" cy=\"47\" r=\"15\" fill=\"none\" stroke=\"" + c + "\" stroke-width=\"3\"/>";
            case 4 ->
                "<path d=\"M36,30 L44,30 L44,43 L57,43 L57,51 L44,51 L44,64 L36,64 L36,51 L23,51 L23,43 L36,43 Z\"" +
                " fill=\"" + c + "\"/>";
            default ->
                "";
        };
    }
}
