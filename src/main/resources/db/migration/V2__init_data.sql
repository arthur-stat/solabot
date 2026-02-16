-- 初始化 t_pjsk_binding 表数据
INSERT INTO t_pjsk_binding (user_id, cn_pjsk_id, jp_pjsk_id, tw_pjsk_id, kr_pjsk_id, en_pjsk_id, default_server_region, created_at, updated_at)
VALUES
    (1256977415, '7485938033569569588', null, null, null, null, 'cn', NOW(), NOW()),
    (1685280357, '7445096955522390818', null, null, null, null, 'cn', NOW(), NOW()),
    (1828209434, '7487212719486049063', null, null, null, null, 'cn', NOW(), NOW()),
    (984097301,  '7486314772426939173', null, null, null, null, 'cn', NOW(), NOW()),
    (1461762986, '7489244575534537481', null, null, null, null, 'cn', NOW(), NOW()),
    (1935545201, '7485918807991769867', null, null, null, null, 'cn', NOW(), NOW()),
    (1935545201, '7485918807991769867', null, null, null, null, 'cn', NOW(), NOW()),
    (2462516428, '7503172168113658687', null, null, null, null, 'cn', NOW(), NOW()),
    (2660564229, '7485918725925935911', null, null, null, null, 'cn', NOW(), NOW()),
    (1355343505, '7486453954226772745', null, null, null, null, 'cn', NOW(), NOW()),
    (3468684112, '7445607211377793833', null, null, null, null, 'cn', NOW(), NOW()),
    (1093664084, '7485918807991769867', null, null, null, null, 'cn', NOW(), NOW()),
    (2647203449, '13156441665941511',  '13156441665941511', null, null, null, 'cn', NOW(), NOW()),
    (2785983443, null, '445447333024854027', null, null, null, 'jp', NOW(), NOW()),
    (2804953970, null, '566088661097308165', null, null, null, 'jp', NOW(), NOW()),
    (3528800641, '7486314772426939173', null, null, null, null, 'cn', NOW(), NOW()),
    (3958746790, '7445088585579125531', null, null, null, null, 'cn', NOW(), NOW())
    ON CONFLICT (user_id)
DO NOTHING;

-- 初始化 t_streamer_alias 表数据
INSERT INTO t_streamer_alias (streamer_id, stream_id, alias, created_at, updated_at)
VALUES
    (299013902, 84074, '炫狗', NOW(), NOW())
    ON CONFLICT (streamer_id, stream_id)
DO NOTHING;

-- 初始化 t_streamer_subscription 表数据
INSERT INTO t_streamer_subscription (user_id, group_id, stream_id, created_at, updated_at)
VALUES
    (1093664084, 793709714, 7734200, NOW(), NOW()),
    (1093664084, 1034714395, 7734200, NOW(), NOW())
    ON CONFLICT (user_id, group_id, stream_id)
DO NOTHING;
