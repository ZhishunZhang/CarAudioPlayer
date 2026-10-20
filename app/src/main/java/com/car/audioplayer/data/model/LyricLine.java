package com.car.audioplayer.data.model;

import java.util.Collections;
import java.util.List;

/**
 * 歌词行（客户端解析 LRC 结果）。
 * 行按 timeMs 升序排列，播放进度与 timeMs 比对得出当前高亮行。
 */
public class LyricLine {

    /** 出现时间（毫秒） */
    public final long timeMs;

    /** 歌词文本 */
    public final String text;

    public LyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }

    /** 空歌词占位 */
    public static List<LyricLine> emptyFallback(String songName) {
        return Collections.singletonList(new LyricLine(0, songName + " - 暂无歌词"));
    }
}