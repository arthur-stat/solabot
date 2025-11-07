import os
import re
import json
import math
from collections import defaultdict
from PIL import Image, ImageDraw, ImageFont

# ---------------- Resources discovery ----------------
DEFAULT_SEARCH_NAMES = [
    "pjsk-mysekai-prototype",
    "pjsk_mysekai_prototype",
    "pjsk-mysekai-prototype.zip",
]
ENV_RES_DIR = os.environ.get("PJ_RES_DIR", None)

def find_resources_dir():
    if ENV_RES_DIR and os.path.exists(ENV_RES_DIR):
        return ENV_RES_DIR
    here = os.getcwd()
    if os.path.exists(os.path.join(here, "paint.html")):
        return here
    for name in DEFAULT_SEARCH_NAMES:
        p = os.path.join(here, name)
        if os.path.exists(os.path.join(p, "paint.html")):
            return p
    fallback = "/mnt/data/pjsk_extracted"
    if os.path.exists(os.path.join(fallback, "paint.html")):
        return fallback
    return here

RES_DIR = find_resources_dir()
PAINT_HTML = os.path.join(RES_DIR, "paint.html")
IMG_DIR = os.path.join(RES_DIR, "img")
ICON_DIR = os.path.join(RES_DIR, "icon")
MISSING_ICON = os.path.join(RES_DIR, "missing.png")

# ---------------- Visual constants ----------------
ICON_SIZE = (40, 40)
ICON_PADDING = 6
ICON_SPACING = 6
ITEMLIST_ROUND_RADIUS = 8

try:
    DEFAULT_FONT = ImageFont.truetype("DejaVuSans.ttf", 24)
    DEFAULT_FONT_QTY = ImageFont.truetype("DejaVuSans.ttf", 16)
except Exception:
    DEFAULT_FONT = ImageFont.load_default()
    DEFAULT_FONT_QTY = ImageFont.load_default()

# ---------------- Parse paint.html JS objects (best-effort) ----------------
def _find_js_object_text(varname, html_text):
    idx = html_text.find(varname)
    if idx == -1:
        return None
    brace = html_text.find("{", idx)
    if brace == -1:
        return None
    i = brace
    depth = 0
    while i < len(html_text):
        ch = html_text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return html_text[brace:i+1]
        i += 1
    return None

def jsobj_to_json_compatible(s):
    s = re.sub(r'//.*?(?=\n|$)', '', s)
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'([{\[,]\s*)([A-Za-z0-9_\-]+)\s*:', r'\1"\2":', s)
    s = re.sub(r"\'([^']*)\'", lambda m: '"' + m.group(1).replace('"', '\\"') + '"', s)
    s = re.sub(r',\s*([}\]])', r'\1', s)
    return s

def parse_js_object_from_html(varname, html_text):
    txt = _find_js_object_text(varname, html_text)
    if not txt:
        return None
    try:
        return json.loads(jsobj_to_json_compatible(txt))
    except Exception:
        return None

_PAINT_HTML_TEXT = ""
if os.path.exists(PAINT_HTML):
    try:
        with open(PAINT_HTML, "r", encoding="utf-8") as fh:
            _PAINT_HTML_TEXT = fh.read()
    except Exception:
        _PAINT_HTML_TEXT = ""

SCENES = parse_js_object_from_html("SCENES", _PAINT_HTML_TEXT) or {}
FIXTURE_COLORS = parse_js_object_from_html("FIXTURE_COLORS", _PAINT_HTML_TEXT) or {}
ITEM_TEXTURES = parse_js_object_from_html("ITEM_TEXTURES", _PAINT_HTML_TEXT) or {}
RARE_ITEM = parse_js_object_from_html("RARE_ITEM", _PAINT_HTML_TEXT) or {}
SUPER_RARE_ITEM = parse_js_object_from_html("SUPER_RARE_ITEM", _PAINT_HTML_TEXT) or {}

# map label <-> sceneKey (best-effort)
LABEL_TO_SCENEKEY = {}
_select_match = re.search(r'<select[^>]*id="sceneSelect"[^>]*>(.*?)</select>', _PAINT_HTML_TEXT, flags=re.S)
if _select_match:
    selects = re.findall(r'<option\s+value="([^"]+)">([^<]+)</option>', _select_match.group(1))
    LABEL_TO_SCENEKEY = {label.strip(): key.strip() for key, label in selects}
SCENE_KEY_TO_LABEL = {v: k for k, v in LABEL_TO_SCENEKEY.items()}

# ---------------- Utilities (unified) ----------------
def _int_try(v):
    try:
        return int(v)
    except Exception:
        return None

def is_entry_rare(category, iid):
    ii = _int_try(iid)
    if ii is None:
        return False
    # SUPER_RARE_ITEM may be dict or list
    if category in SUPER_RARE_ITEM:
        vals = SUPER_RARE_ITEM.get(category) or []
        if isinstance(vals, dict):
            if ii in { _int_try(k) for k in vals.keys() }:
                return True
        else:
            if ii in vals:
                return True
    if category in RARE_ITEM:
        vals = RARE_ITEM.get(category) or []
        if isinstance(vals, dict):
            if ii in { _int_try(k) for k in vals.keys() }:
                return True
        else:
            if ii in vals:
                return True
    return False

def _resolve_texture_path_from_ref(texture_ref):
    if not texture_ref:
        return MISSING_ICON if os.path.exists(MISSING_ICON) else None
    tr = texture_ref
    if tr.startswith("./"):
        tr = tr[2:]
    # candidate relative to RES_DIR
    candidate = os.path.join(RES_DIR, tr)
    if os.path.exists(candidate):
        return candidate
    # try direct absolute-like path (if user provided absolute)
    if os.path.exists(texture_ref):
        return texture_ref
    # try ICON_DIR with basename
    cand2 = os.path.join(ICON_DIR, os.path.basename(tr))
    if os.path.exists(cand2):
        return cand2
    # fallback to missing
    if os.path.exists(MISSING_ICON):
        return MISSING_ICON
    return None

def _texture_for_category_id(cat, iid):
    texture = None
    if cat in ITEM_TEXTURES:
        ctmap = ITEM_TEXTURES[cat]
        # try string key first
        if str(iid) in ctmap:
            texture = ctmap[str(iid)]
        else:
            # try int key if mapping uses ints
            try:
                texture = ctmap[int(iid)]
            except Exception:
                texture = None
    if cat == "mysekai_music_record":
        texture = "./icon/Texture2D/item_surplus_music_record.png"
    if not texture:
        texture = "./missing.png"
    return texture

def entries_from_point(p, *, only_rare=False):
    reward = p.get("reward", {}) or {}
    entries = []
    for cat, dic in reward.items():
        # dic might be a dict of id->qty
        for iid, qty in dic.items():
            iid_s = str(iid)
            try:
                qty_i = int(qty)
            except Exception:
                qty_i = 1
            if only_rare and not is_entry_rare(cat, iid):
                continue
            texture_ref = _texture_for_category_id(cat, iid)
            entries.append((cat, iid_s, qty_i, texture_ref))
    return entries

# ---------------- Drawing helpers ----------------
def draw_icon_with_qty(canvas_img, icon_ref, top_left_xy, qty):
    x, y = int(top_left_xy[0]), int(top_left_xy[1])
    tex_path = _resolve_texture_path_from_ref(icon_ref)
    try:
        icon = Image.open(tex_path).convert("RGBA").resize(ICON_SIZE, Image.Resampling.LANCZOS)
    except Exception:
        icon = Image.new("RGBA", ICON_SIZE, (200,200,200,255))
    canvas_img.paste(icon, (x, y), icon)

    draw = ImageDraw.Draw(canvas_img)
    qty_text = str(qty)
    tx = x + 2
    ty = y + 2
    try:
        draw.text((tx, ty), qty_text, font=DEFAULT_FONT_QTY,
                  fill=(255,255,255,255), stroke_width=2, stroke_fill=(0,0,0,255))
    except TypeError:
        draw.text((tx+1, ty+1), qty_text, font=DEFAULT_FONT_QTY, fill=(0,0,0,255))
        draw.text((tx, ty), qty_text, font=DEFAULT_FONT_QTY, fill=(255,255,255,255))
    return ICON_SIZE

def _draw_icon_no_badge(canvas_img, icon_ref, top_left_xy):
    x, y = int(top_left_xy[0]), int(top_left_xy[1])
    tex_path = _resolve_texture_path_from_ref(icon_ref)
    try:
        icon = Image.open(tex_path).convert("RGBA").resize(ICON_SIZE, Image.Resampling.LANCZOS)
    except Exception:
        icon = Image.new("RGBA", ICON_SIZE, (200,200,200,255))
    canvas_img.paste(icon, (x, y), icon)
    return ICON_SIZE

# ---------------- Scene (map) rendering ----------------
def render_scene_tile(scene_key, points_list, *, only_rare=False):
    scene_cfg = SCENES.get(scene_key, {})
    image_path = None
    if scene_cfg and scene_cfg.get("imagePath"):
        image_path = os.path.join(RES_DIR, scene_cfg.get("imagePath"))
    if not image_path or not os.path.exists(image_path):
        # fallback: first png in IMG_DIR
        if os.path.isdir(IMG_DIR):
            imgs = [os.path.join(IMG_DIR, f) for f in os.listdir(IMG_DIR) if f.lower().endswith(".png")]
            if imgs:
                image_path = imgs[0]
    if not image_path or not os.path.exists(image_path):
        base_img = Image.new("RGBA", (1920, 1080), (255,255,255,255))
    else:
        base_img = Image.open(image_path).convert("RGBA")

    w, h = base_img.size
    draw = ImageDraw.Draw(base_img)

    physical_width = float(scene_cfg.get("physicalWidth", 10))
    offsetX = float(scene_cfg.get("offsetX", 0))
    offsetY = float(scene_cfg.get("offsetY", 0))
    xDirection = scene_cfg.get("xDirection", "x+")
    yDirection = scene_cfg.get("yDirection", "y+")
    reverseXY = bool(scene_cfg.get("reverseXY", False))

    displayGridWidth = physical_width * (w / w)
    originX = w / 2 + offsetX
    originY = h / 2 + offsetY

    marker_r = 5

    # build item blocks using entries_from_point to ensure identical behavior
    item_blocks = []
    for p in (points_list or []):
        loc = p.get("location", [0,0])
        if reverseXY and isinstance(loc, (list, tuple)) and len(loc) >= 2:
            loc = [loc[1], loc[0]]
        x_grid = float(loc[0])
        y_grid = float(loc[1])
        displayX = originX + (x_grid * displayGridWidth if xDirection == "x+" else -x_grid * displayGridWidth)
        displayY = originY + (y_grid * displayGridWidth if yDirection == "y+" else -y_grid * displayGridWidth)

        fixture_id = p.get("fixtureId", "")
        color_hex = None
        if str(fixture_id) in FIXTURE_COLORS:
            color_hex = FIXTURE_COLORS.get(str(fixture_id))
        else:
            try:
                color_hex = FIXTURE_COLORS.get(int(fixture_id))
            except Exception:
                color_hex = None

        if color_hex:
            hexc = color_hex.lstrip("#")
            try:
                fill = tuple(int(hexc[i:i+2], 16) for i in (0,2,4)) + (255,)
            except Exception:
                fill = (0,0,0,255)
            draw.ellipse((displayX-marker_r, displayY-marker_r, displayX+marker_r, displayY+marker_r), fill=fill)
            # stroke if contains rare
            contains_any_rare = False
            for cat,iid,_,_ in entries_from_point(p, only_rare=False):
                if is_entry_rare(cat, iid):
                    contains_any_rare = True
                    break
            stroke_color = (255,0,0,255) if contains_any_rare else (0,0,0,255)
            draw.ellipse((displayX-marker_r, displayY-marker_r, displayX+marker_r, displayY+marker_r), outline=stroke_color)
        else:
            draw.text((displayX-6, displayY-10), "?", fill=(0,0,0,255), font=DEFAULT_FONT)

        # entries for this point, honoring only_rare
        entries = entries_from_point(p, only_rare=only_rare)
        if not entries:
            continue

        total_w = ICON_PADDING*2 + len(entries)*ICON_SIZE[0] + (len(entries)-1)*ICON_SPACING
        total_h = ICON_PADDING*2 + ICON_SIZE[1]
        tlx = displayX - total_w/2
        tly = displayY - marker_r - total_h - 6
        if tly < 0:
            tly = displayY + marker_r + 6
        tlx = max(0, min(tlx, w - total_w))
        tly = max(0, min(tly, h - total_h))

        has_super = any(is_entry_rare(cat, iid) and (cat in SUPER_RARE_ITEM) for cat, iid, _, _ in entries)
        has_rare = any(is_entry_rare(cat, iid) for cat, iid, _, _ in entries)

        item_blocks.append({
            "tlx": int(tlx), "tly": int(tly), "w": int(total_w), "h": int(total_h),
            "entries": entries, "has_super": has_super, "has_rare": has_rare,
            "displayX": displayX, "displayY": displayY
        })

    # paste backgrounds and icons
    for blk in item_blocks:
        bb_w, bb_h = blk["w"], blk["h"]
        if blk["has_super"]:
            bg = (255, 0, 0, int(255*0.5))
        elif blk["has_rare"]:
            bg = (0, 0, 180, int(255*0.5))
        else:
            bg = (138, 138, 138, int(255*0.7))
        overlay = Image.new("RGBA", (bb_w, bb_h), (0,0,0,0))
        ovdraw = ImageDraw.Draw(overlay)
        try:
            ovdraw.rounded_rectangle((0,0,bb_w,bb_h), radius=ITEMLIST_ROUND_RADIUS, fill=bg)
        except Exception:
            ovdraw.rectangle((0,0,bb_w,bb_h), fill=bg)
        base_img.paste(overlay, (blk["tlx"], blk["tly"]), overlay)

        curx = blk["tlx"] + ICON_PADDING
        cury = blk["tly"] + ICON_PADDING
        for cat, iid, qty, tex_ref in blk["entries"]:
            draw_icon_with_qty(base_img, tex_ref, (curx, cury), qty)
            curx += ICON_SIZE[0] + ICON_SPACING

    return base_img

# ---------------- Composite (map assembly) ----------------
SCENE_ORDER = ["scene1", "scene2", "scene3", "scene4"]

def render_composite(all_parsed_json, *, only_rare=False):
    # determine tile size
    first_scene = SCENES.get(SCENE_ORDER[0], {}) if SCENES else {}
    first_img_path = None
    if first_scene.get("imagePath"):
        candidate = os.path.join(RES_DIR, first_scene.get("imagePath"))
        if os.path.exists(candidate):
            first_img_path = candidate
    if not first_img_path and os.path.isdir(IMG_DIR):
        imgs = [os.path.join(IMG_DIR, f) for f in os.listdir(IMG_DIR) if f.lower().endswith(".png")]
        if imgs:
            first_img_path = imgs[0]
    if first_img_path and os.path.exists(first_img_path):
        tile_w, tile_h = Image.open(first_img_path).size
    else:
        tile_w, tile_h = (1920, 1080)

    comp_w, comp_h = tile_w * 2, tile_h * 2
    comp = Image.new("RGBA", (comp_w, comp_h), (255,255,255,255))

    scene_key_to_label = {v: k for k, v in LABEL_TO_SCENEKEY.items()}

    positions = {
        "scene1": (0, 0),
        "scene2": (tile_w, 0),
        "scene3": (0, tile_h),
        "scene4": (tile_w, tile_h)
    }

    for sk in SCENE_ORDER:
        label = scene_key_to_label.get(sk)
        points = []
        if label and label in all_parsed_json:
            points = all_parsed_json[label]
        elif sk in all_parsed_json:
            points = all_parsed_json[sk]
        else:
            points = []
        tile_img = render_scene_tile(sk, points, only_rare=only_rare)
        comp.paste(tile_img, positions[sk])

    return comp

# ---------------- Overview generation (fixed & consistent) ----------------
def _gather_counts_by_scene(all_parsed_json, *, only_rare=False):
    counts_per_scene = {}
    total_counts = defaultdict(int)
    scene_key_to_label = {v: k for k, v in LABEL_TO_SCENEKEY.items()}

    labels_in_order = []
    for idx, sk in enumerate(SCENE_ORDER, start=1):
        # overview 暂时使用 "Map 1" ... 的标题（避免日语编码问题）
        header_label = f"Map {idx}"
        labels_in_order.append(header_label)
        # find points using same resolution logic as map
        lookup_label = scene_key_to_label.get(sk)
        if lookup_label and lookup_label in all_parsed_json:
            points = all_parsed_json[lookup_label] or []
        elif sk in all_parsed_json:
            points = all_parsed_json[sk] or []
        else:
            points = []
        c = defaultdict(int)
        for p in points:
            entries = entries_from_point(p, only_rare=only_rare)
            for cat, iid, qty, tex_ref in entries:
                key = (cat, str(iid))
                c[key] += int(qty)
                total_counts[key] += int(qty)
        counts_per_scene[header_label] = c
    return counts_per_scene, total_counts, labels_in_order

def render_overview(all_parsed_json, *, only_rare=False, max_width=2000):
    counts_per_scene, total_counts, labels_in_order = _gather_counts_by_scene(all_parsed_json, only_rare=only_rare)

    # filter items with non-zero total
    unique_items = [k for k, v in total_counts.items() if v > 0]
    # sort by global total desc
    unique_items.sort(key=lambda k: -total_counts[k])

    # if nothing, return small empty image
    if not unique_items:
        img = Image.new("RGBA", (min(800, max(400, int(max_width))), 200), (255,255,255,255))
        d = ImageDraw.Draw(img)
        msg = "No resources found"
        try:
            bbox = d.textbbox((0,0), msg, font=DEFAULT_FONT)
            d.text(((img.width - (bbox[2]-bbox[0]))/2, (img.height - (bbox[3]-bbox[1]))/2), msg, font=DEFAULT_FONT, fill=(0,0,0,255))
        except Exception:
            d.text((10,10), msg, font=DEFAULT_FONT, fill=(0,0,0,255))
        return img

    # determine target width: base on first map composite width but capped
    try:
        comp = render_composite(all_parsed_json, only_rare=only_rare)
        target_width = min(comp.width, max_width)
    except Exception:
        target_width = min(1000, max_width)

    padding = 24
    per_item_w = ICON_SIZE[0] + ICON_SPACING
    icons_per_row = max(1, (target_width - 2*padding) // per_item_w)

    # compute section heights dynamically (each section only needs as many rows as items in that scene)
    section_heights = []
    scene_items_lists = []
    for label in labels_in_order:
        items_in_scene = [key for key in unique_items if counts_per_scene[label].get(key, 0) > 0]
        scene_items_lists.append(items_in_scene)
        rows_needed = math.ceil(len(items_in_scene) / icons_per_row) if items_in_scene else 1
        section_h = 36 + rows_needed * (ICON_SIZE[1] + 6 + ICON_SPACING) + padding   # header + icons + qty text
        section_heights.append(section_h)

    # summary section (Total) uses all unique_items
    summary_items = unique_items
    summary_rows = math.ceil(len(summary_items)/icons_per_row)
    summary_h = 36 + summary_rows * (ICON_SIZE[1] + 6 + ICON_SPACING) + padding
    total_height = sum(section_heights) + summary_h + padding

    img = Image.new("RGBA", (target_width, int(total_height)), (255,255,255,255))
    d = ImageDraw.Draw(img)

    y_cursor = padding
    bg_pad = 4
    icon_bg_color = (240,240,240,220)
    rare_outline = (220, 40, 40, 255)
    section_div_color = (230,230,230,255)

    # pre-resolve textures mapping for unique_items to avoid repeated lookup
    textures = {}
    for (cat, iid) in unique_items:
        tex_ref = _texture_for_category_id(cat, iid)
        textures[(cat, iid)] = tex_ref

    def draw_items_block(x0, y0, items_list, counts_dict):
        # items_list: list of keys to draw (in order)
        if not items_list:
            # draw nothing but a small placeholder
            return 0
        rows = math.ceil(len(items_list)/icons_per_row)
        for idx, key in enumerate(items_list):
            cnt = counts_dict.get(key, 0)
            if cnt <= 0:
                continue
            row = idx // icons_per_row
            col = idx % icons_per_row
            # compute centered start for this row
            items_in_this_row = min(len(items_list)-row*icons_per_row, icons_per_row)
            total_row_w = items_in_this_row * ICON_SIZE[0] + (items_in_this_row - 1) * ICON_SPACING
            row_x0 = int((target_width - total_row_w) / 2)
            icon_x = row_x0 + col * (ICON_SIZE[0] + ICON_SPACING)
            icon_y = y0 + row * (ICON_SIZE[1] + 6 + ICON_SPACING)
            # background rounded small
            bx0 = icon_x - bg_pad
            by0 = icon_y - bg_pad
            bx1 = icon_x + ICON_SIZE[0] + bg_pad
            by1 = icon_y + ICON_SIZE[1] + bg_pad
            try:
                d.rounded_rectangle((bx0, by0, bx1, by1), radius=6, fill=icon_bg_color)
            except Exception:
                d.rectangle((bx0, by0, bx1, by1), fill=icon_bg_color)

            _draw_icon_no_badge(img, textures[key], (icon_x, icon_y))

            # rare outline
            if is_entry_rare(key[0], key[1]):
                try:
                    d.ellipse((icon_x-2, icon_y-2, icon_x+ICON_SIZE[0]+2, icon_y+ICON_SIZE[1]+2), outline=rare_outline, width=2)
                except Exception:
                    d.rectangle((icon_x-2, icon_y-2, icon_x+ICON_SIZE[0]+2, icon_y+ICON_SIZE[1]+2), outline=rare_outline)

            # draw count centered below icon
            cnt_text = str(cnt)
            try:
                tb = d.textbbox((0,0), cnt_text, font=DEFAULT_FONT_QTY)
                tw = tb[2]-tb[0]
                th = tb[3]-tb[1]
            except Exception:
                tw, th = (len(cnt_text)*8, 12)
            tx = icon_x + (ICON_SIZE[0] - tw)/2
            ty = icon_y + ICON_SIZE[1] + 8  # line spacing
            d.text((tx+1, ty+1), cnt_text, font=DEFAULT_FONT_QTY, fill=(0,0,0,255))
            d.text((tx, ty), cnt_text, font=DEFAULT_FONT_QTY, fill=(255,255,255,255))
        return rows * (ICON_SIZE[1] + 6 + ICON_SPACING)

    # draw four scene sections
    for i, label in enumerate(labels_in_order):
        # header
        d.text((padding, y_cursor+6), label, font=DEFAULT_FONT, fill=(0,0,0,255))
        items_list = scene_items_lists[i]
        # draw items block starting a bit lower
        start_y = y_cursor + 36
        draw_items_block(padding, start_y, items_list, counts_per_scene[label])
        # divider
        div_y = y_cursor + section_heights[i] - 6
        d.line((padding, div_y, target_width - padding, div_y), fill=section_div_color)
        y_cursor += section_heights[i]

    # total summary
    d.text((padding, y_cursor+6), "Total", font=DEFAULT_FONT, fill=(0,0,0,255))
    start_y = y_cursor + 36
    draw_items_block(padding, start_y, unique_items, total_counts)

    return img

# ---------------- Public API ----------------
def render_and_save(parsed_data: dict, out_map_path: str, out_overview_path: str, *, only_rare: bool = False, to_rgb: bool = True, **save_kwargs):
    comp = render_composite(parsed_data or {}, only_rare=only_rare)
    if to_rgb:
        comp_to_save = comp.convert("RGB")
    else:
        comp_to_save = comp
    comp_to_save.save(out_map_path, **save_kwargs)

    overview_img = render_overview(parsed_data or {}, only_rare=only_rare, max_width=1000)
    if to_rgb:
        ov_to_save = overview_img.convert("RGB")
    else:
        ov_to_save = overview_img
    ov_to_save.save(out_overview_path, **save_kwargs)
    return out_map_path, out_overview_path

# ---------------- CLI for quick test ----------------
if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Render map composite and overview image from parsed JSON.")
    parser.add_argument("--in", dest="infile", help="parsed JSON file", required=False)
    parser.add_argument("--out-map", dest="outmap", help="output map image path", default="out_map.png")
    parser.add_argument("--out-overview", dest="outov", help="output overview image path", default="out_overview.png")
    parser.add_argument("--only-rare", dest="only_rare", action="store_true", help="only render rare items")
    args = parser.parse_args()
    parsed = {}
    if args.infile and os.path.exists(args.infile):
        with open(args.infile, "r", encoding="utf-8") as f:
            parsed = json.load(f)
    else:
        sample_paths = [
            os.path.join(RES_DIR, "parsed_data.json"),
            os.path.join(RES_DIR, "parsed_data(示例).json"),
            os.path.join(RES_DIR, "parsed_data（示例）.json"),
        ]
        for p in sample_paths:
            if os.path.exists(p):
                with open(p, "r", encoding="utf-8") as fh:
                    parsed = json.load(fh)
                break
    if not parsed:
        print("No parsed JSON provided/found. Exiting.")
    else:
        mp, ov = render_and_save(parsed, args.outmap, args.outov, only_rare=args.only_rare)
        print("Saved map:", mp)
        print("Saved overview:", ov)