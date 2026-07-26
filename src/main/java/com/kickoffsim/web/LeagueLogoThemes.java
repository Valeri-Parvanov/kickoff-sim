package com.kickoffsim.web;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class LeagueLogoThemes {

    public record Theme(String baseFrom, String baseTo, String motif, String overflow, String... keywords) {
        public boolean hasOverflow() {
            return overflow != null && !overflow.isEmpty();
        }
    }

    private static final String[] TOPPERS = {

            "<path d=\"M27 27 L29.5 15 L35 21.5 L40 12 L45 21.5 L50.5 15 L53 27 Z\" fill=\"url(#%1$s)\"/>"
                    + "<rect x=\"27\" y=\"27\" width=\"26\" height=\"4\" rx=\"1.2\" fill=\"url(#%1$s)\"/>"
                    + "<circle cx=\"40\" cy=\"17.5\" r=\"1.7\" fill=\"#fff\" fill-opacity=\"0.85\"/>"
                    + "<circle cx=\"30.6\" cy=\"19.5\" r=\"1.2\" fill=\"#fff\" fill-opacity=\"0.6\"/>"
                    + "<circle cx=\"49.4\" cy=\"19.5\" r=\"1.2\" fill=\"#fff\" fill-opacity=\"0.6\"/>",

            "<path d=\"M34 13 h12 v7 c0 5.2 -2.9 8.3 -6 9.2 C36.9 28.3 34 25.2 34 20 Z\" fill=\"url(#%1$s)\"/>"
                    + "<path d=\"M34.3 15 C29.6 15 29.1 21.4 34.3 22.8\" stroke=\"url(#%1$s)\" stroke-width=\"2\" fill=\"none\" stroke-linecap=\"round\"/>"
                    + "<path d=\"M45.7 15 C50.4 15 50.9 21.4 45.7 22.8\" stroke=\"url(#%1$s)\" stroke-width=\"2\" fill=\"none\" stroke-linecap=\"round\"/>"
                    + "<rect x=\"38.7\" y=\"29\" width=\"2.6\" height=\"3.4\" fill=\"url(#%1$s)\"/>"
                    + "<path d=\"M35.6 32.4 h8.8 l1.5 2.8 h-11.8 z\" fill=\"url(#%1$s)\"/>"
                    + "<path d=\"M36.4 16 h7.2 v4.4 c0 3 -1.6 4.9 -3.6 5.6 -2 -.7 -3.6 -2.6 -3.6 -5.6 z\" fill=\"#fff\" fill-opacity=\"0.22\"/>",

            "<path d=\"M34 11 l4.4 8.4 h3.2 L37.2 11 z\" fill=\"#c2413f\"/>"
                    + "<path d=\"M46 11 l-4.4 8.4 h-3.2 L42.8 11 z\" fill=\"#9e2f2e\"/>"
                    + "<circle cx=\"40\" cy=\"26\" r=\"7.6\" fill=\"url(#%1$s)\"/>"
                    + "<circle cx=\"40\" cy=\"26\" r=\"5\" fill=\"#fff\" fill-opacity=\"0.25\"/>"
                    + "<path d=\"M40 21.6 l1.4 3 l3.2 .4 l-2.3 2.2 l.6 3.2 l-2.9 -1.6 l-2.9 1.6 l.6 -3.2 l-2.3 -2.2 l3.2 -.4 z\" fill=\"#fff\" fill-opacity=\"0.9\"/>",

            "<path d=\"M29 31 C23.5 24 26 15 33 11.5\" stroke=\"url(#%1$s)\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\"/>"
                    + "<path d=\"M51 31 C56.5 24 54 15 47 11.5\" stroke=\"url(#%1$s)\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\"/>"
                    + "<g fill=\"url(#%1$s)\">"
                    + "<ellipse cx=\"27.6\" cy=\"25\" rx=\"3\" ry=\"1.6\" transform=\"rotate(-40 27.6 25)\"/>"
                    + "<ellipse cx=\"28.4\" cy=\"19\" rx=\"3\" ry=\"1.6\" transform=\"rotate(-58 28.4 19)\"/>"
                    + "<ellipse cx=\"32\" cy=\"14\" rx=\"3\" ry=\"1.6\" transform=\"rotate(-72 32 14)\"/>"
                    + "<ellipse cx=\"52.4\" cy=\"25\" rx=\"3\" ry=\"1.6\" transform=\"rotate(40 52.4 25)\"/>"
                    + "<ellipse cx=\"51.6\" cy=\"19\" rx=\"3\" ry=\"1.6\" transform=\"rotate(58 51.6 19)\"/>"
                    + "<ellipse cx=\"48\" cy=\"14\" rx=\"3\" ry=\"1.6\" transform=\"rotate(72 48 14)\"/></g>"
                    + "<path d=\"M40 14 l2.6 5.4 l5.8 .8 l-4.2 4 l1 5.8 l-5.2 -2.8 l-5.2 2.8 l1 -5.8 l-4.2 -4 l5.8 -.8 z\" fill=\"url(#%1$s)\"/>",

            "<path d=\"M40 11 l3.2 6.6 l7.2 1 l-5.2 5.1 l1.2 7.2 l-6.4 -3.4 l-6.4 3.4 l1.2 -7.2 l-5.2 -5.1 l7.2 -1 z\" fill=\"url(#%1$s)\"/>"
                    + "<path d=\"M24 28 h32 l-3 5.6 h-26 z\" fill=\"url(#%1$s)\" fill-opacity=\"0.85\"/>"
                    + "<path d=\"M27 33.6 h26 l-2 3 h-22 z\" fill=\"#000\" fill-opacity=\"0.18\"/>"
    };

    private static final List<Theme> THEMES = List.of(

            new Theme("#8e2020", "#3d0d0d",
                    "<path d=\"M22 66 C26 62 32 68 38 65 C44 62 50 68 56 64 C60 61 60 70 52 71 C42 72 26 72 22 66 Z\" fill=\"#a01d1d\"/>"
                            + "<ellipse cx=\"40\" cy=\"51\" rx=\"15\" ry=\"13\" fill=\"#d93a2b\"/>"
                            + "<path d=\"M28 56 c3 5 7 7 12 7 c-6 3 -13 1 -16 -3 z\" fill=\"#7d1515\" fill-opacity=\"0.55\"/>"
                            + "<path d=\"M40 38 l-5 3 l-4 -2 l2 4 l-4 2 l5 1 l1 3 l3 -3 l3 3 l1 -3 l5 -1 l-4 -2 l2 -4 l-4 2 z\" fill=\"#3f7d34\"/>"
                            + "<ellipse cx=\"34\" cy=\"46\" rx=\"3.4\" ry=\"2.4\" fill=\"#fff\" fill-opacity=\"0.28\"/>"
                            + "<circle cx=\"49\" cy=\"58\" r=\"1.5\" fill=\"#5c1010\"/><circle cx=\"45\" cy=\"62\" r=\"1\" fill=\"#5c1010\"/>",
                    "<path d=\"M12 24 C19 17 24 26 31 19 C36 14 45 14 50 19 C57 26 62 17 68 24 C68 31 59 34 40 34 C21 34 12 31 12 24 Z\" fill=\"#c62828\"/>"
                            + "<path d=\"M26 33 c0 7 -1 11 2 11 c3 0 2 -4 2 -11 z\" fill=\"#c62828\"/>"
                            + "<path d=\"M48 34 c0 9 -1 13 2 13 c3 0 2 -4 2 -13 z\" fill=\"#c62828\"/>"
                            + "<circle cx=\"36\" cy=\"44\" r=\"2.4\" fill=\"#c62828\"/><circle cx=\"58\" cy=\"41\" r=\"1.6\" fill=\"#c62828\"/>"
                            + "<circle cx=\"20\" cy=\"40\" r=\"1.4\" fill=\"#c62828\"/>",
                    "domat"),

            new Theme("#4a7fd4", "#1b3a73",
                    "<path d=\"M20 62 C24 54 32 57 36 52 C40 47 48 49 52 55 C56 61 62 60 62 64 C62 68 50 70 40 70 C30 70 18 68 20 62 Z\" fill=\"#fff\"/>"
                            + "<ellipse cx=\"31\" cy=\"66\" rx=\"3\" ry=\"1.6\" fill=\"#dbe6f5\"/>"
                            + "<ellipse cx=\"47\" cy=\"65\" rx=\"4\" ry=\"1.8\" fill=\"#dbe6f5\"/>"
                            + "<circle cx=\"60\" cy=\"49\" r=\"2.4\" fill=\"#fff\"/><circle cx=\"22\" cy=\"50\" r=\"1.8\" fill=\"#fff\"/>"
                            + "<g transform=\"rotate(-32 44 40)\"><path d=\"M38 33 h13 l-2 15 h-9 z\" fill=\"#eaf1fb\"/>"
                            + "<rect x=\"37.4\" y=\"31.6\" width=\"14.2\" height=\"2.6\" rx=\"1.1\" fill=\"#c8d8ee\"/></g>",
                    "<path d=\"M11 23 C17 15 23 25 30 17 C35 11 45 11 50 17 C57 25 63 15 69 23 C69 31 60 35 40 35 C20 35 11 31 11 23 Z\" fill=\"#ffffff\"/>"
                            + "<path d=\"M20 34 c0 7 -1 11 2 11 c3 0 2 -4 2 -11 z\" fill=\"#ffffff\"/>"
                            + "<path d=\"M40 35 c0 9 -1 14 2 14 c3 0 2 -5 2 -14 z\" fill=\"#ffffff\"/>"
                            + "<path d=\"M57 33 c0 6 -1 9 1.6 9 c2.6 0 1.6 -3 1.6 -9 z\" fill=\"#ffffff\"/>"
                            + "<circle cx=\"30\" cy=\"46\" r=\"2.2\" fill=\"#ffffff\"/><circle cx=\"52\" cy=\"49\" r=\"1.6\" fill=\"#ffffff\"/>",
                    "ayrian", "ayran"),

            new Theme("#9a6516", "#43290a",
                    "<path d=\"M24 52 c-2 -8 6 -14 14 -12 c5 -6 15 -3 15 5 c6 3 4 13 -3 14 c-2 7 -13 8 -17 3 c-7 2 -11 -4 -9 -10 z\" fill=\"#e8b45c\"/>"
                            + "<path d=\"M31 47 c4 -3 9 -2 12 1\" stroke=\"#b9781f\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M35 58 c5 2 10 1 13 -2\" stroke=\"#b9781f\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>"
                            + "<g fill=\"#fff\"><circle cx=\"33\" cy=\"43\" r=\"1.3\"/><circle cx=\"45\" cy=\"41.5\" r=\"1\"/>"
                            + "<circle cx=\"52\" cy=\"50\" r=\"1.2\"/><circle cx=\"27\" cy=\"55\" r=\"1\"/><circle cx=\"42\" cy=\"62\" r=\"1.2\"/></g>",
                    "", "mekitsa", "kyoftavitsa"),

            new Theme("#3f8f6b", "#12402d",
                    "<path d=\"M23 46 h34 l-4 20 c-.5 3 -22 3 -22.5 0 z\" fill=\"#f2f6f3\"/>"
                            + "<ellipse cx=\"40\" cy=\"46\" rx=\"17\" ry=\"5.4\" fill=\"#fff\"/>"
                            + "<ellipse cx=\"40\" cy=\"46\" rx=\"14.5\" ry=\"4.2\" fill=\"#e8efe9\"/>"
                            + "<circle cx=\"34\" cy=\"45.4\" r=\"3\" fill=\"#9ed37f\"/><circle cx=\"34\" cy=\"45.4\" r=\"1.6\" fill=\"#d9f0c8\"/>"
                            + "<circle cx=\"45\" cy=\"47\" r=\"2.6\" fill=\"#8ac96c\"/><circle cx=\"45\" cy=\"47\" r=\"1.4\" fill=\"#d9f0c8\"/>"
                            + "<circle cx=\"39.5\" cy=\"43.6\" r=\"2.2\" fill=\"#a8db8b\"/>",
                    "", "tarator", "snezhanka", "mizeria"),

            new Theme("#6b4a2f", "#2b1a0e",
                    "<path d=\"M31 41 h18 l-2.5 28 c-.3 2.5 -13 2.5 -13.3 0 z\" fill=\"#e9e2d6\" fill-opacity=\"0.28\"/>"
                            + "<path d=\"M32 46 h16 l-2.2 23 c-.3 2 -11.6 2 -11.9 0 z\" fill=\"#8a6030\"/>"
                            + "<ellipse cx=\"40\" cy=\"41.5\" rx=\"9\" ry=\"3\" fill=\"#f3ede2\"/>"
                            + "<ellipse cx=\"36.5\" cy=\"40.4\" rx=\"2.6\" ry=\"1.9\" fill=\"#fff\"/>"
                            + "<ellipse cx=\"43\" cy=\"41\" rx=\"2.2\" ry=\"1.6\" fill=\"#fff\"/>"
                            + "<path d=\"M35 50 c1 6 1 10 0 15\" stroke=\"#fff\" stroke-opacity=\"0.22\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>",
                    "<path d=\"M11 24 C18 16 24 26 31 18 C36 12 44 12 49 18 C56 26 62 16 69 24 C69 32 60 36 40 36 C20 36 11 32 11 24 Z\" fill=\"#8a6030\"/>"
                            + "<path d=\"M11 22 C18 14 24 24 31 16 C36 10 44 10 49 16 C56 24 62 14 69 22 C69 26 58 28 40 28 C22 28 11 26 11 22 Z\" fill=\"#a9773f\"/>"
                            + "<path d=\"M22 35 c0 6 -1 9 2 9 c3 0 2 -3 2 -9 z\" fill=\"#8a6030\"/>"
                            + "<path d=\"M44 36 c0 8 -1 12 2 12 c3 0 2 -4 2 -12 z\" fill=\"#8a6030\"/>"
                            + "<path d=\"M58 34 c0 5 -1 8 1.6 8 c2.6 0 1.6 -3 1.6 -8 z\" fill=\"#8a6030\"/>"
                            + "<ellipse cx=\"46\" cy=\"50\" rx=\"2.2\" ry=\"2.8\" fill=\"#8a6030\"/>"
                            + "<ellipse cx=\"24\" cy=\"47\" rx=\"1.6\" ry=\"2.2\" fill=\"#8a6030\"/>",
                    "boza"),

            new Theme("#5b7ea8", "#1d3350",
                    "<path d=\"M24 44 l16 -7 l16 7 l-16 7 z\" fill=\"#ffffff\"/>"
                            + "<path d=\"M24 44 v14 l16 7 v-14 z\" fill=\"#eef1f4\"/>"
                            + "<path d=\"M56 44 v14 l-16 7 v-14 z\" fill=\"#d9dee6\"/>"
                            + "<g fill=\"#cdd4dd\"><circle cx=\"32\" cy=\"52\" r=\"2\"/><circle cx=\"35\" cy=\"59\" r=\"1.4\"/></g>"
                            + "<g fill=\"#c2c9d3\"><circle cx=\"47\" cy=\"52\" r=\"1.7\"/><circle cx=\"50\" cy=\"58\" r=\"1.2\"/></g>",
                    "", "sirene"),

            new Theme("#7a3b1c", "#341207",
                    "<ellipse cx=\"40\" cy=\"64\" rx=\"20\" ry=\"5\" fill=\"#2b2b2b\"/>"
                            + "<g stroke=\"#6b6b6b\" stroke-width=\"1.6\" stroke-linecap=\"round\">"
                            + "<path d=\"M24 62 h32\"/><path d=\"M25 66 h30\"/></g>"
                            + "<ellipse cx=\"40\" cy=\"52\" rx=\"15\" ry=\"10\" fill=\"#8b4a22\"/>"
                            + "<ellipse cx=\"40\" cy=\"50\" rx=\"13\" ry=\"8\" fill=\"#a75c2b\"/>"
                            + "<g stroke=\"#5e2f12\" stroke-width=\"1.8\" stroke-linecap=\"round\">"
                            + "<path d=\"M30 47 l6 5\"/><path d=\"M38 45 l6 5\"/><path d=\"M46 48 l4 4\"/></g>"
                            + "<path d=\"M33 40 c1 -4 -2 -5 0 -8\" stroke=\"#ffd9a0\" stroke-opacity=\"0.55\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M47 40 c1 -4 -2 -5 0 -8\" stroke=\"#ffd9a0\" stroke-opacity=\"0.4\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>",
                    "", "kyufte", "kebapche", "skara"),

            new Theme("#a06a1e", "#432a08",
                    "<circle cx=\"40\" cy=\"52\" r=\"17\" fill=\"#e0a63f\"/>"
                            + "<path d=\"M40 52 m0 -14 a14 14 0 1 1 -9.9 4.1\" fill=\"none\" stroke=\"#c1832a\" stroke-width=\"3.4\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M40 52 m0 -8.5 a8.5 8.5 0 1 1 -6 2.5\" fill=\"none\" stroke=\"#c1832a\" stroke-width=\"3.2\" stroke-linecap=\"round\"/>"
                            + "<circle cx=\"40\" cy=\"52\" r=\"2.6\" fill=\"#c1832a\"/>"
                            + "<circle cx=\"40\" cy=\"52\" r=\"17\" fill=\"none\" stroke=\"#f5cd7d\" stroke-width=\"1.4\" stroke-opacity=\"0.7\"/>"
                            + "<g fill=\"#fff8e6\"><circle cx=\"32\" cy=\"44\" r=\"1\"/><circle cx=\"49\" cy=\"57\" r=\"1\"/></g>",
                    "", "banitsa", "tikvenik"),

            new Theme("#5c6b2e", "#22290d",
                    "<rect x=\"33\" y=\"36\" width=\"14\" height=\"5\" rx=\"1.6\" fill=\"#dfe6ee\"/>"
                            + "<path d=\"M36 41 h8 l3 8 v16 c0 2.5 -14 2.5 -14 0 v-16 z\" fill=\"#eaf0f6\" fill-opacity=\"0.35\"/>"
                            + "<path d=\"M34.4 52 h11.2 v13 c0 2 -11.2 2 -11.2 0 z\" fill=\"#e8e3d0\"/>"
                            + "<ellipse cx=\"40\" cy=\"52\" rx=\"5.6\" ry=\"1.8\" fill=\"#f5f1e2\"/>"
                            + "<path d=\"M52 44 l4 -3 l2 3 l-4 3 z\" fill=\"#f5cd7d\" fill-opacity=\"0.6\"/>",
                    "", "rakia"),

            new Theme("#8c2f1a", "#3a1006",
                    "<path d=\"M31 38 h18 v4 h-18 z\" fill=\"#d8d3c4\"/>"
                            + "<path d=\"M29.5 42 h21 v22 c0 3 -21 3 -21 0 z\" fill=\"#e6e1d3\" fill-opacity=\"0.3\"/>"
                            + "<path d=\"M31 45 h18 v18 c0 2.2 -18 2.2 -18 0 z\" fill=\"#c4341c\"/>"
                            + "<ellipse cx=\"40\" cy=\"45\" rx=\"9\" ry=\"2.4\" fill=\"#e04a2c\"/>"
                            + "<rect x=\"33\" y=\"50\" width=\"14\" height=\"8\" rx=\"1.4\" fill=\"#f2e9d2\" fill-opacity=\"0.85\"/>"
                            + "<path d=\"M35.5 53.5 h9\" stroke=\"#9a2a14\" stroke-width=\"1.4\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M35.5 56 h6\" stroke=\"#9a2a14\" stroke-width=\"1.2\" stroke-linecap=\"round\"/>",
                    "<path d=\"M12 22 C20 16 25 25 32 18 C37 13 45 13 50 18 C57 25 62 16 68 22 C68 30 58 33 40 33 C22 33 12 30 12 22 Z\" fill=\"#c4341c\"/>"
                            + "<path d=\"M24 32 c0 8 -1 12 2 12 c3 0 2 -4 2 -12 z\" fill=\"#c4341c\"/>"
                            + "<path d=\"M42 33 c0 11 -1 16 2 16 c3 0 2 -5 2 -16 z\" fill=\"#c4341c\"/>"
                            + "<path d=\"M58 31 c0 6 -1 9 1.6 9 c2.6 0 1.6 -3 1.6 -9 z\" fill=\"#c4341c\"/>"
                            + "<circle cx=\"33\" cy=\"48\" r=\"2\" fill=\"#c4341c\"/>",
                    "lyutenitsa", "zimnitsa"),

            new Theme("#356b3a", "#0f2a12",
                    "<path d=\"M27 46 c6 -10 20 -10 26 0 c3 6 -2 12 -13 12 c-11 0 -16 -6 -13 -12 z\" fill=\"#5fa845\"/>"
                            + "<path d=\"M40 40 c-6 4 -9 10 -8 16\" stroke=\"#3d7a2c\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M40 40 c6 4 9 10 8 16\" stroke=\"#3d7a2c\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>"
                            + "<ellipse cx=\"40\" cy=\"62\" rx=\"13\" ry=\"4\" fill=\"#4a8f36\"/>"
                            + "<circle cx=\"34\" cy=\"49\" r=\"2\" fill=\"#d6e9c2\" fill-opacity=\"0.7\"/>",
                    "", "sarma", "salata", "gradinska", "chubritsa"),

            new Theme("#7a4a22", "#2e1608",
                    "<path d=\"M26 44 h28 l-3 20 c-.4 3 -21.6 3 -22 0 z\" fill=\"#c98a4b\"/>"
                            + "<ellipse cx=\"40\" cy=\"44\" rx=\"14\" ry=\"4.4\" fill=\"#e0a35e\"/>"
                            + "<rect x=\"24\" y=\"38\" width=\"32\" height=\"5\" rx=\"2.4\" fill=\"#a86a34\"/>"
                            + "<rect x=\"37\" y=\"33\" width=\"6\" height=\"5\" rx=\"1.6\" fill=\"#a86a34\"/>"
                            + "<path d=\"M31 30 c1 -4 -2 -5 0 -8\" stroke=\"#ffd9a0\" stroke-opacity=\"0.45\" stroke-width=\"1.6\" fill=\"none\" stroke-linecap=\"round\"/>",
                    "", "kapama"),

            new Theme("#8a5a2a", "#2f1a06",
                    "<ellipse cx=\"40\" cy=\"48\" rx=\"14\" ry=\"5\" fill=\"#c8442c\"/>"
                            + "<ellipse cx=\"34\" cy=\"47\" rx=\"3\" ry=\"2.2\" fill=\"#e9f3dd\"/>"
                            + "<ellipse cx=\"45\" cy=\"49\" rx=\"3.4\" ry=\"2.4\" fill=\"#8ac96c\"/>"
                            + "<path d=\"M26 50 h28 l-2.5 15 c-.3 2.4 -22.7 2.4 -23 0 z\" fill=\"#f2f6f3\"/>"
                            + "<g fill=\"#e2ecdf\"><circle cx=\"35\" cy=\"57\" r=\"2\"/><circle cx=\"45\" cy=\"59\" r=\"1.6\"/></g>",
                    "", "shopska"),

            new Theme("#6d4b18", "#2a1a05",
                    "<circle cx=\"40\" cy=\"50\" r=\"9\" fill=\"#4a3410\"/>"
                            + "<g fill=\"#f2c33d\"><ellipse cx=\"40\" cy=\"36\" rx=\"3.6\" ry=\"7\"/><ellipse cx=\"40\" cy=\"64\" rx=\"3.6\" ry=\"7\"/>"
                            + "<ellipse cx=\"26\" cy=\"50\" rx=\"7\" ry=\"3.6\"/><ellipse cx=\"54\" cy=\"50\" rx=\"7\" ry=\"3.6\"/>"
                            + "<ellipse cx=\"30\" cy=\"40\" rx=\"6\" ry=\"3.4\" transform=\"rotate(-45 30 40)\"/>"
                            + "<ellipse cx=\"50\" cy=\"40\" rx=\"6\" ry=\"3.4\" transform=\"rotate(45 50 40)\"/>"
                            + "<ellipse cx=\"30\" cy=\"60\" rx=\"6\" ry=\"3.4\" transform=\"rotate(45 30 60)\"/>"
                            + "<ellipse cx=\"50\" cy=\"60\" rx=\"6\" ry=\"3.4\" transform=\"rotate(-45 50 60)\"/></g>"
                            + "<circle cx=\"40\" cy=\"50\" r=\"5.4\" fill=\"#6b4a14\"/>",
                    "", "sunflower", "sunday"),

            new Theme("#6b4a24", "#2a1806",
                    "<circle cx=\"31\" cy=\"39\" r=\"5\" fill=\"#7a5024\"/><circle cx=\"49\" cy=\"39\" r=\"5\" fill=\"#7a5024\"/>"
                            + "<circle cx=\"31\" cy=\"39\" r=\"2.6\" fill=\"#a5764a\"/><circle cx=\"49\" cy=\"39\" r=\"2.6\" fill=\"#a5764a\"/>"
                            + "<circle cx=\"40\" cy=\"50\" r=\"13\" fill=\"#8b5c2c\"/>"
                            + "<ellipse cx=\"40\" cy=\"55\" rx=\"7.5\" ry=\"6\" fill=\"#d9b184\"/>"
                            + "<circle cx=\"35.5\" cy=\"47\" r=\"1.7\" fill=\"#2c1a07\"/><circle cx=\"44.5\" cy=\"47\" r=\"1.7\" fill=\"#2c1a07\"/>"
                            + "<ellipse cx=\"40\" cy=\"53\" rx=\"2.4\" ry=\"1.8\" fill=\"#2c1a07\"/>"
                            + "<path d=\"M36 57 c2.4 2 5.6 2 8 0\" stroke=\"#2c1a07\" stroke-width=\"1.2\" fill=\"none\" stroke-linecap=\"round\"/>",
                    "", "mecho"),

            new Theme("#1f2a4a", "#080d1c",
                    "<path d=\"M50 42 a13 13 0 1 1 -12 -12 a10 10 0 0 0 12 12 z\" fill=\"#f5e6a8\"/>"
                            + "<g fill=\"#fff\"><circle cx=\"27\" cy=\"58\" r=\"1.6\"/><circle cx=\"52\" cy=\"58\" r=\"1.2\"/>"
                            + "<circle cx=\"40\" cy=\"66\" r=\"1.4\"/><circle cx=\"33\" cy=\"63\" r=\"1\"/><circle cx=\"57\" cy=\"48\" r=\"1\"/></g>",
                    "", "midnight", "noshten"),

            new Theme("#2b2b2b", "#0d0d0d",
                    "<rect x=\"22\" y=\"38\" width=\"36\" height=\"26\" rx=\"2\" fill=\"#f4f4f4\"/>"
                            + "<g fill=\"#111\"><rect x=\"26\" y=\"42\" width=\"2.5\" height=\"14\"/><rect x=\"30\" y=\"42\" width=\"1.4\" height=\"14\"/>"
                            + "<rect x=\"33\" y=\"42\" width=\"3.2\" height=\"14\"/><rect x=\"38\" y=\"42\" width=\"1.4\" height=\"14\"/>"
                            + "<rect x=\"41\" y=\"42\" width=\"2.4\" height=\"14\"/><rect x=\"45\" y=\"42\" width=\"1.2\" height=\"14\"/>"
                            + "<rect x=\"48\" y=\"42\" width=\"3\" height=\"14\"/><rect x=\"53\" y=\"42\" width=\"1.6\" height=\"14\"/></g>"
                            + "<rect x=\"26\" y=\"58\" width=\"28\" height=\"2.4\" rx=\"1\" fill=\"#9a9a9a\"/>",
                    "", "barcode"),

            new Theme("#3a2a1a", "#140d06",
                    "<path d=\"M31 38 h18 l-2.5 27 c-.3 2.4 -12.7 2.4 -13 0 z\" fill=\"#e8eef2\" fill-opacity=\"0.32\"/>"
                            + "<path d=\"M32.4 46 h15.2 l-2 19 c-.2 1.8 -11 1.8 -11.2 0 z\" fill=\"#6b4423\"/>"
                            + "<ellipse cx=\"40\" cy=\"46\" rx=\"7.6\" ry=\"2.4\" fill=\"#8a5a30\"/>"
                            + "<g fill=\"#cfe4ee\" fill-opacity=\"0.85\"><rect x=\"34\" y=\"49\" width=\"5\" height=\"5\" rx=\"1\" transform=\"rotate(12 36.5 51.5)\"/>"
                            + "<rect x=\"41\" y=\"54\" width=\"4.6\" height=\"4.6\" rx=\"1\" transform=\"rotate(-18 43 56)\"/></g>"
                            + "<rect x=\"45\" y=\"30\" width=\"2.6\" height=\"16\" rx=\"1.3\" fill=\"#e05a7a\" transform=\"rotate(14 46 38)\"/>",
                    "", "kafe", "ledeno"),

            new Theme("#4a4a52", "#17171c",
                    "<circle cx=\"32\" cy=\"48\" r=\"7\" fill=\"none\" stroke=\"#dfe3ea\" stroke-width=\"2.2\"/>"
                            + "<circle cx=\"48\" cy=\"48\" r=\"7\" fill=\"none\" stroke=\"#dfe3ea\" stroke-width=\"2.2\"/>"
                            + "<path d=\"M39 48 h2\" stroke=\"#dfe3ea\" stroke-width=\"2.2\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M25 46 l-4 -3\" stroke=\"#dfe3ea\" stroke-width=\"2.2\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M55 46 l4 -3\" stroke=\"#dfe3ea\" stroke-width=\"2.2\" stroke-linecap=\"round\"/>"
                            + "<path d=\"M30 62 c6 -4 14 -4 20 0\" stroke=\"#dfe3ea\" stroke-width=\"2\" fill=\"none\" stroke-linecap=\"round\"/>",
                    "", "pensioner")
    );

    private LeagueLogoThemes() {
    }

    public static Optional<Theme> match(String leagueName) {
        if (leagueName == null || leagueName.isBlank()) {
            return Optional.empty();
        }
        String lower = leagueName.toLowerCase(Locale.ROOT);
        for (Theme theme : THEMES) {
            for (String keyword : theme.keywords()) {
                if (lower.contains(keyword)) {
                    return Optional.of(theme);
                }
            }
        }
        return Optional.empty();
    }

    public static int topperCount() {
        return TOPPERS.length;
    }

    public static String topper(int index, String goldGradientId) {
        return TOPPERS[Math.floorMod(index, TOPPERS.length)].formatted(goldGradientId);
    }
}
