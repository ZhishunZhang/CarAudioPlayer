package com.car.audioplayer.player;

import com.car.audioplayer.data.model.LyricLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析：把 [mm:ss.xx] 标记的文本解析为带时间戳的歌词行列表。
 */
public class LyricParser {

    /** 匹配 [mm:ss.xxx] 或 [mm:ss] */
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");

    /**
     * 解析 LRC 文本。
     * @return 按时间升序的歌词行；无有效标记时返回空列表（调用方用占位文案）。
     */
    public static List<LyricLine> parse(String lrc) {
        List<LyricLine> result = new ArrayList<>();
        if (lrc == null || lrc.trim().isEmpty()) {
            return result;
        }
        String[] lines = lrc.split("\\r?\\n");
        for (String line : lines) {
            Matcher m = TIME_TAG.matcher(line);
            List<Long> times = new ArrayList<>();
            String text = line;
            while (m.find()) {
                long mm = Long.parseLong(m.group(1));
                long ss = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = 0;
                if (frac != null) {
                    // 1~3 位小数补齐为毫秒
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) {
                        ms *= 100;
                    } else if (frac.length() == 2) {
                        ms *= 10;
                    }
                }
                times.add((mm * 60 + ss) * 1000 + ms);
            }
            if (times.isEmpty()) {
                continue;
            }
            text = TIME_TAG.matcher(line).replaceAll("").trim();
            for (long t : times) {
                result.add(new LyricLine(t, text));
            }
        }
        result.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return result;
    }

    /** 按播放进度（毫秒）定位当前歌词行下标；无匹配返回 -1 */
    public static int findLineIndex(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= positionMs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }
}