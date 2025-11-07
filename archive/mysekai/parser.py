import os
import time
import json
from typing import List, Optional, Union
import msgpack
import msgspec
from msgspec import Struct as BaseModel
from sssekai.crypto.APIManager import decrypt, SEKAI_APIMANAGER_KEYSETS
from config import MYSEKAI_PARSED_DIR, MYSEKAI_PARSE_FAIL_DIR

class UserMysekaiSiteHarvestFixture(BaseModel):
    mysekaiSiteHarvestFixtureId: int
    positionX: int
    positionZ: int
    hp: int
    userMysekaiSiteHarvestFixtureStatus: str

class UserMysekaiSiteHarvestResourceDrop(BaseModel):
    resourceType: str
    resourceId: int
    positionX: int
    positionZ: int
    hp: int
    seq: int
    mysekaiSiteHarvestResourceDropStatus: str
    quantity: int

class Map(BaseModel, kw_only=True):
    mysekaiSiteId: int
    siteName: Optional[str] = None
    userMysekaiSiteHarvestFixtures: List[UserMysekaiSiteHarvestFixture]
    userMysekaiSiteHarvestResourceDrops: List[UserMysekaiSiteHarvestResourceDrop]

SITE_ID = {
    1: "マイホーム",
    2: "1F",
    3: "2F",
    4: "3F",
    5: "さいしょの原っぱ",
    6: "願いの砂浜",
    7: "彩りの花畑",
    8: "忘れ去られた場所",
}

def parse_map(mysekai_data: Union[dict, bytes, bytearray], game_server: str, user_id: Union[str, int]) -> dict:
    try:
        if isinstance(mysekai_data, (bytes, bytearray)):
            try:
                decrypted_data = decrypt(mysekai_data, SEKAI_APIMANAGER_KEYSETS[game_server])
            except Exception as e:
                raise ValueError(f"deserialize binary file failed: {e}")
            try:
                mysekai_data = msgpack.unpackb(decrypted_data)
            except Exception as e:
                raise ValueError(f"decrypt failed: {e}\nthis may be caused by incorrect game server parameters or an invalid key")
        
        elif not isinstance(mysekai_data, dict):
            raise TypeError("unknown file type, expect for bytes, bytearray or dict")
        
        try:
            harvest_maps_data = mysekai_data["updatedResources"]["userMysekaiHarvestMaps"]

        except KeyError as e:
            timestamp = time.strftime("%Y%m%d_%H%M%S", time.localtime())
            filename = f"{game_server}_{user_id}_{timestamp}.json"
            filepath = os.path.join(MYSEKAI_PARSE_FAIL_DIR, filename)
            
            try:
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(mysekai_data, f, ensure_ascii=False, indent=4)
            except Exception as e:
                raise IOError(e)
            
            raise ValueError(f"invalid data, missing necessary key: {e}\nthe json file has been saved to {filepath}\n数据不完整，退回至标题界面重新进入mysekai即可。后续版本可能会解决这一问题，不再需要退回至标题界面。")
    
    except Exception as e:
        raise RuntimeError(f"parse failed: {e}")

    harvest_maps: List[Map] = [ 
        msgspec.json.decode(msgspec.json.encode(mp), type=Map) for mp in mysekai_data["updatedResources"]["userMysekaiHarvestMaps"]
    ]

    for mp in harvest_maps:
        mp.siteName = SITE_ID.get(mp.mysekaiSiteId, f"unknown site {mp.mysekaiSiteId}")

    processed_map = {}
    for mp in harvest_maps:
        print(f"Site: {mp.siteName}")
        mp_detail = []
        
        # 已生成收获物
        for fixture in mp.userMysekaiSiteHarvestFixtures:
            if fixture.userMysekaiSiteHarvestFixtureStatus == "spawned":
                mp_detail.append( 
                    {
                        "location": (fixture.positionX, fixture.positionZ),
                        "fixtureId": fixture.mysekaiSiteHarvestFixtureId,
                        "reward": {}
                    }
                )
        
        # 掉落资源
        for drop in mp.userMysekaiSiteHarvestResourceDrops:
            pos = (drop.positionX, drop.positionZ)
            for i in range(len(mp_detail)):
                if mp_detail[i]["location"] != pos:
                    continue
                
                mp_detail[i]["reward"].setdefault(drop.resourceType, {})
                mp_detail[i]["reward"][drop.resourceType][drop.resourceId] = \
                    mp_detail[i]["reward"][drop.resourceType].get(drop.resourceId, 0) + drop.quantity
                break
        
        processed_map[mp.siteName] = mp_detail

    filename = f"{game_server}_{user_id}.json"
    filepath = os.path.join(MYSEKAI_PARSED_DIR, filename)
    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(processed_map, f, ensure_ascii=False, indent=4)
    except Exception as e:
        raise IOError(e)
    
    return processed_map

if __name__ == "__main__":
    file_path = input("待解析文件：").strip()
    server = input("游戏服务器：").strip()
    
    try:
        with open(file_path, "rb") as f:
            mysekai_data = f.read()
        
        result = parse_map(mysekai_data, server)
        
        for site_name, items in result.items():
            print(f"\n=== {site_name} ===")
            for item in items:
                print(f"loc: {item['location']}, award: {item['reward']}")
        
        output_path = "parsed_result.json"
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2, ensure_ascii=False)
        
        print(f"\n解析成功，结果已保存为 {output_path}")
    
    except FileNotFoundError:
        print(f"错误：找不到文件 '{file_path}'")
    except json.JSONDecodeError:
        print(f"错误：文件 '{file_path}' 解析失败")
    except Exception as e:
        print(f"发生错误：{str(e)}")