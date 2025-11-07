# 服务器配置
IN_PORT = 8849
OUT_PORT = 443
LOCAL_IP = 'yly.dylancloud.uk'

# 文件路径配置
MYSEKAI_RAW_DIR = "/home/yly/bot/solabot/data/pjsk_user_data/mysekai/raw"                      # 未解密未解析的原始响应存储路径
MYSEKAI_PARSED_DIR = "/home/yly/bot/solabot/data/pjsk_user_data/mysekai/parsed"                # 解析成功后数据的存储路径
MYSEKAI_PARSE_FAIL_DIR = "/home/yly/bot/solabot/data/pjsk_user_data/mysekai/parse_fail"        # 解析失败后已解密数据转存路径
MYSEKAI_DRAW_MAP_DIR = "/home/yly/bot/solabot/data/pjsk_user_data/mysekai/draw/map"            # 全地图透视图存储路径
MYSEKAI_DRAW_OVERVIEW_DIR = "/home/yly/bot/solabot/data/pjsk_user_data/mysekai/draw/overview"  # 资源概览图片存储路径

# 游戏服务器域名映射
GAME_SERVER_MAP = {
    "mkcn-prod-public-60001-1.dailygn.com": "cn",
    "mkcn-prod-public-60001-2.dailygn.com": "cn",
    "production-game-api.sekai.colorfulpalette.org": "jp",
    "mk-zian-obt-cdn.bytedgame.com": "tw",
    "mkkorea-obt-prod01-cdn.bytedgame.com": "kr",
    "n-production-game-api.sekai-en.com": "en"
}

# 项目配置
ONLY_RARE = False  # 全地图透视图是否仅显示珍贵收获