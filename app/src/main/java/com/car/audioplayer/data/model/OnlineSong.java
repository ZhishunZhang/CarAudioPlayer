package com.car.audioplayer.data.model;

/**
 * 在线歌曲（来自中转服务接口，覆盖搜索/歌单/榜单/专辑/歌手等全部场景）。
 * 在线歌曲只在内存，不入库；
 * 收藏场景单独走 FavoriteSong 实体。
 */
public class OnlineSong {

    /** 歌曲 MID：获取播放链接与封面的钥匙。榜单快路径下初始为空，播放前按需解析回填 */
    public String mid;

    /** QQ 歌曲数字 ID：榜单快路径（resolveMid=false）只有它，播放时用它换 mid */
    public long songId;

    /** 歌名 */
    public final String name;

    /** 歌手（多歌手用 / 拼接） */
    public final String singer;

    /** 歌手 MID（第一阶段歌手，跳歌手页用） */
    public final String singerMid;

    /** 专辑名 */
    public final String album;

    /** 专辑/封面 MID（拼封面图 URL 用） */
    public final String albumMid;

    /** 时长（秒） */
    public final int interval;

    /** 是否 VIP 付费歌曲（pay_play=1）：列表显示角标，点击大概率取不到播放地址 */
    public boolean payPlay;

    /** 播放地址（运行时由 getMusicPlay 获取后回填，不参与构造） */
    public String playUrl;

    /** 兼容旧调用（搜索页单曲播放） */
    public OnlineSong(String mid, String name, String singer) {
        this(mid, name, singer, "", "", "", "", 0);
    }

    /** 全参构造：封面 MID 优先取 imageMid（部分接口字段名不同），为空回退 albumMid */
    public OnlineSong(String mid, String name, String singer, String singerMid,
                      String album, String albumMid, String imageMid, int interval) {
        this.mid = mid;
        this.name = name;
        this.singer = singer;
        this.singerMid = singerMid;
        this.album = album;
        // 封面 MID：部分接口给 imageMid/strMediaMid，优先用；否则回退 albumMid
        String cover = (imageMid != null && !imageMid.isEmpty()) ? imageMid : albumMid;
        this.albumMid = cover == null ? "" : cover;
        this.interval = interval;
    }

    /** 是否具备跳转专辑页的条件 */
    public boolean hasAlbum() {
        return albumMid != null && !albumMid.isEmpty();
    }

    /** 是否具备跳转歌手页的条件 */
    public boolean hasSinger() {
        return singerMid != null && !singerMid.isEmpty();
    }

    /** 是否已拿到可播放地址 */
    public boolean hasPlayUrl() {
        return playUrl != null && !playUrl.isEmpty();
    }
}