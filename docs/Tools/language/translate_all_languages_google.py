import os
import re
import glob
import time
import urllib.request
import urllib.parse
import json
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed

RES_DIR = r"C:/Users/bou/StudioProjects/AMARadio/app/src/main/res"

FOLDER_TO_GOOGLE_LANG = {
    'values-af': 'af', 'values-am': 'am', 'values-ar': 'ar', 'values-az': 'az',
    'values-be': 'be', 'values-bg': 'bg', 'values-bn': 'bn', 'values-bs': 'bs',
    'values-ca': 'ca', 'values-cs': 'cs', 'values-da': 'da', 'values-de': 'de',
    'values-el': 'el', 'values-es': 'es', 'values-et': 'et', 'values-eu': 'eu',
    'values-fa': 'fa', 'values-fi': 'fi', 'values-fil': 'tl', 'values-fr': 'fr',
    'values-ga': 'ga', 'values-gl': 'gl', 'values-gu': 'gu', 'values-he': 'iw',
    'values-hi': 'hi', 'values-hr': 'hr', 'values-hu': 'hu', 'values-hy': 'hy',
    'values-in': 'id', 'values-is': 'is', 'values-it': 'it', 'values-ja': 'ja',
    'values-ka': 'ka', 'values-kk': 'kk', 'values-km': 'km', 'values-kn': 'kn',
    'values-ko': 'ko', 'values-lt': 'lt', 'values-lv': 'lv', 'values-mk': 'mk',
    'values-ml': 'ml', 'values-mr': 'mr', 'values-ms': 'ms', 'values-mt': 'mt',
    'values-my': 'my', 'values-nb': 'no', 'values-ne': 'ne', 'values-nl': 'nl',
    'values-nn': 'no', 'values-pl': 'pl', 'values-pt': 'pt', 'values-pt-rBR': 'pt',
    'values-ro': 'ro', 'values-ru': 'ru', 'values-si': 'si', 'values-sk': 'sk',
    'values-sl': 'sl', 'values-sq': 'sq', 'values-sr': 'sr', 'values-sv': 'sv',
    'values-sw': 'sw', 'values-ta': 'ta', 'values-te': 'te', 'values-th': 'th',
    'values-tr': 'tr', 'values-uk': 'uk', 'values-ur': 'ur', 'values-uz': 'uz',
    'values-vi': 'vi', 'values-zh-rCN': 'zh-CN', 'values-zh-rHK': 'zh-TW',
    'values-zh-rTW': 'zh-TW', 'values-zu': 'zu'
}

GERMAN_MANUAL = {
    "widget_name_small": "AMARadio Kompakt",
    "widget_name_full": "AMARadio Player",
    "widget_description_small": "Schnelle Player-Steuerung",
    "widget_description_full": "Player und Senderliste",
    "error_no_file_manager": "Keine Dateimanager-App gefunden. Bitte stelle sicher, dass ein Datei-Explorer installiert und aktiviert ist.",
    "error_no_browser": "Kein Webbrowser gefunden. Bitte installiere einen Browser, um diesen Link zu öffnen.",
    "error_invalid_url": "Bitte gib eine gültige URL ein",
    "detail_create_shortcut": "Zum Startbildschirm hinzufügen",
    "context_menu_create_shortcut": "@string/detail_create_shortcut"
}

def translate_google(text, target_lang):
    if not text or text.startswith("@string/"):
        return text

    if text in ["AMARadio Player", "AMARadio Compact"]:
        return text

    placeholders = []
    def mask_match(match):
        placeholders.append(match.group(0))
        return f" XPLACEHOLDER{len(placeholders)-1}X "

    pattern = r'%(\d+\$)?[0-9\.\-]*[a-zA-Z]'
    masked_text = re.sub(pattern, mask_match, text)
    masked_text = masked_text.replace("\\n", " XNEWLINEHANDLERX ")

    url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=' + target_lang + '&dt=t&q=' + urllib.parse.quote(masked_text)
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})

    for attempt in range(3):
        try:
            res = urllib.request.urlopen(req, timeout=5)
            data = json.loads(res.read().decode('utf-8'))

            translated = ""
            for chunk in data[0]:
                if chunk[0]:
                    translated += chunk[0]

            for idx, original_ph in enumerate(placeholders):
                ph_pattern = re.compile(rf'\s*XPLACEHOLDER{idx}X\s*', re.IGNORECASE)
                translated = ph_pattern.sub(original_ph, translated)

            translated = re.sub(r'\s*XNEWLINEHANDLERX\s*', '\\\\n', translated)
            return translated.strip()
        except Exception as e:
            time.sleep(0.3)

    return text

def parse_string_keys(file_path):
    keys = {}
    if not os.path.exists(file_path):
        return keys
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        for child in root:
            if child.tag == 'string':
                name = child.attrib.get('name')
                translatable = child.attrib.get('translatable', 'true')
                if translatable != 'false' and name:
                    keys[name] = child.text or ''
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
    return keys

def format_xml_value(text):
    if text.startswith("@string/"):
        return text
    val = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val = val.replace("&amp;amp;", "&amp;")
    val = re.sub(r"(?<!\\)'", r"\'", val)
    return val

def process_language(lang_dir, master_keys):
    folder_name = os.path.basename(lang_dir)
    file_path = os.path.join(lang_dir, "strings.xml")
    if not os.path.exists(file_path):
        return

    existing_keys = parse_string_keys(file_path)
    missing_keys = [k for k in master_keys if k not in existing_keys]

    if not missing_keys:
        return

    target_lang = FOLDER_TO_GOOGLE_LANG.get(folder_name, 'en')
    print(f"Processing {folder_name} ({target_lang}) - {len(missing_keys)} missing keys...")

    raw_content = open(file_path, "r", encoding="utf-8").read()
    new_lines = []

    for k in missing_keys:
        eng_text = master_keys[k]

        if folder_name == "values-de" and k in GERMAN_MANUAL:
            translated_text = GERMAN_MANUAL[k]
        elif eng_text.startswith("@string/"):
            translated_text = eng_text
        else:
            translated_text = translate_google(eng_text, target_lang)

        xml_val = format_xml_value(translated_text)
        new_lines.append(f'    <string name="{k}">{xml_val}</string>')

    insert_block = "\n" + "\n".join(new_lines) + "\n</resources>"
    updated_content = raw_content.replace("</resources>", insert_block)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(updated_content)

    print(f"--> Finished {folder_name}!")

def main():
    default_path = os.path.join(RES_DIR, "values", "strings.xml")
    default_content = open(default_path, "r", encoding="utf-8").read()

    if 'name="detail_create_shortcut"' not in default_content:
        new_keys = '''    <string name="detail_create_shortcut">Add to home screen</string>
    <string name="context_menu_create_shortcut">@string/detail_create_shortcut</string>
</resources>'''
        default_content = default_content.replace("</resources>", new_keys)
        with open(default_path, "w", encoding="utf-8") as f:
            f.write(default_content)

    master_keys = parse_string_keys(default_path)
    print(f"Master translatable keys count: {len(master_keys)}")

    all_lang_dirs = sorted(glob.glob(os.path.join(RES_DIR, "values-*")))

    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = [executor.submit(process_language, lang_dir, master_keys) for lang_dir in all_lang_dirs]
        for future in as_completed(futures):
            future.result()

    print("ALL LANGUAGES COMPLETED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
