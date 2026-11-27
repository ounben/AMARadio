import os
import re

translations_data = """
[values-af]
<string name="accessibility_sleep_timer_active_remaining">Aktief, %1$s oor</string>

[values-am]
<string name="accessibility_sleep_timer_active_remaining">ንቁ፣ %1$s ይቀራል</string>

[values-ar]
<string name="accessibility_sleep_timer_active_remaining">نشط، يتبقى %1$s</string>

[values-as]
<string name="accessibility_sleep_timer_active_remaining">সক্ৰিয়, %1$s বাকী আছে</string>

[values-az]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, %1$s qalıb</string>

[values-be]
<string name="accessibility_sleep_timer_active_remaining">Актыўны, засталося %1$s</string>

[values-bg]
<string name="accessibility_sleep_timer_active_remaining">Активен, остава %1$s</string>

[values-bn]
<string name="accessibility_sleep_timer_active_remaining">সক্রিয়, %1$s বাকি</string>

[values-bs]
<string name="accessibility_sleep_timer_active_remaining">Aktivan, preostalo %1$s</string>

[values-ca]
<string name="accessibility_sleep_timer_active_remaining">Actiu, queda %1$s</string>

[values-cs]
<string name="accessibility_sleep_timer_active_remaining">Aktivní, zbývá %1$s</string>

[values-da]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, %1$s tilbage</string>

[values-de]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, %1$s verbleibend</string>

[values-el]
<string name="accessibility_sleep_timer_active_remaining">Ενεργό, απομένει %1$s</string>

[values-es]
<string name="accessibility_sleep_timer_active_remaining">Activo, queda %1$s</string>

[values-et]
<string name="accessibility_sleep_timer_active_remaining">Aktiivne, %1$s jäänud</string>

[values-eu]
<string name="accessibility_sleep_timer_active_remaining">Aktibo, %1$s geratzen da</string>

[values-fa]
<string name="accessibility_sleep_timer_active_remaining">فعال، %1$s باقی مانده است</string>

[values-fi]
<string name="accessibility_sleep_timer_active_remaining">Aktiivinen, %1$s jäljellä</string>

[values-fil]
<string name="accessibility_sleep_timer_active_remaining">Aktibo, %1$s ang natitira</string>

[values-fr]
<string name="accessibility_sleep_timer_active_remaining">Actif, %1$s restant</string>

[values-ga]
<string name="accessibility_sleep_timer_active_remaining">Gníomhach, %1$s fágtha</string>

[values-gl]
<string name="accessibility_sleep_timer_active_remaining">Activo, queda %1$s</string>

[values-gu]
<string name="accessibility_sleep_timer_active_remaining">સક્રિય, %1$s બાકી</string>

[values-ha]
<string name="accessibility_sleep_timer_active_remaining">Mai aiki, saura %1$s</string>

[values-he]
<string name="accessibility_sleep_timer_active_remaining">פעיל, נותרו %1$s</string>

[values-hi]
<string name="accessibility_sleep_timer_active_remaining">सक्रिय, %1$s शेष</string>

[values-hr]
<string name="accessibility_sleep_timer_active_remaining">Aktivno, preostalo %1$s</string>

[values-hu]
<string name="accessibility_sleep_timer_active_remaining">Aktív, %1$s van hátra</string>

[values-hy]
<string name="accessibility_sleep_timer_active_remaining">Ակտիվ է, մնացել է %1$s</string>

[values-id]
<string name="accessibility_sleep_timer_active_remaining">Aktif, tersisa %1$s</string>

[values-ig]
<string name="accessibility_sleep_timer_active_remaining">Na-arụ ọrụ, %1$s fọdụrụ</string>

[values-in]
<string name="accessibility_sleep_timer_active_remaining">Aktif, tersisa %1$s</string>

[values-is]
<string name="accessibility_sleep_timer_active_remaining">Virkt, %1$s eftir</string>

[values-it]
<string name="accessibility_sleep_timer_active_remaining">Attivo, %1$s rimanente</string>

[values-iw]
<string name="accessibility_sleep_timer_active_remaining">פעיל, נותרו %1$s</string>

[values-ja]
<string name="accessibility_sleep_timer_active_remaining">有効、残り %1$s</string>

[values-ji]
<string name="accessibility_sleep_timer_active_remaining">אַקטיוו, %1$s פֿאַרבליבן</string>

[values-ka]
<string name="accessibility_sleep_timer_active_remaining">აქტიურია, დარჩენილია %1$s</string>

[values-kk]
<string name="accessibility_sleep_timer_active_remaining">Белсенді, %1$s қалды</string>

[values-km]
<string name="accessibility_sleep_timer_active_remaining">សកម្ម នៅសល់ %1$s</string>

[values-kn]
<string name="accessibility_sleep_timer_active_remaining">ಸಕ್ರಿಯ, %1$s ಉಳಿದಿದೆ</string>

[values-ko]
<string name="accessibility_sleep_timer_active_remaining">활성, %1$s 남음</string>

[values-ky]
<string name="accessibility_sleep_timer_active_remaining">Активдүү, %1$s калды</string>

[values-lo]
<string name="accessibility_sleep_timer_active_remaining">ເປີດໃຊ້ງານຢູ່, ຍັງເຫຼືອ %1$s</string>

[values-lt]
<string name="accessibility_sleep_timer_active_remaining">Aktyvus, liko %1$s</string>

[values-lv]
<string name="accessibility_sleep_timer_active_remaining">Aktīvs, atlicis %1$s</string>

[values-mk]
<string name="accessibility_sleep_timer_active_remaining">Активен, преостанува %1$s</string>

[values-ml]
<string name="accessibility_sleep_timer_active_remaining">സജീവം, %1$s ശേഷിക്കുന്നു</string>

[values-mn]
<string name="accessibility_sleep_timer_active_remaining">Идэвхтэй, %1$s үлдсэн</string>

[values-mr]
<string name="accessibility_sleep_timer_active_remaining">सक्रिय, %1$s शिल्लक</string>

[values-ms]
<string name="accessibility_sleep_timer_active_remaining">Aktif, berbaki %1$s</string>

[values-mt]
<string name="accessibility_sleep_timer_active_remaining">Attiv, fadal %1$s</string>

[values-my]
<string name="accessibility_sleep_timer_active_remaining">ဖွင့်ထားသည်၊ %1$s ကျန်သေးသည်</string>

[values-nb]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, %1$s gjenstår</string>

[values-ne]
<string name="accessibility_sleep_timer_active_remaining">सक्रिय, %1$s बाँकी</string>

[values-nl]
<string name="accessibility_sleep_timer_active_remaining">Actief, %1$s resterend</string>

[values-nn]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, %1$s att</string>

[values-or]
<string name="accessibility_sleep_timer_active_remaining">ସକ୍ରିୟ, %1$s ବାକି ଅଛି</string>

[values-pa]
<string name="accessibility_sleep_timer_active_remaining">ਕਿਰਿਆਸ਼ੀਲ, %1$s ਬਾਕੀ</string>

[values-pl]
<string name="accessibility_sleep_timer_active_remaining">Aktywny, pozostało %1$s</string>

[values-ps]
<string name="accessibility_sleep_timer_active_remaining">فعال، %1$s پاتې دي</string>

[values-pt]
<string name="accessibility_sleep_timer_active_remaining">Ativo, %1$s restante</string>

[values-pt-rBR]
<string name="accessibility_sleep_timer_active_remaining">Ativo, %1$s restante</string>

[values-rm]
<string name="accessibility_sleep_timer_active_remaining">Activ, anc %1$s</string>

[values-ro]
<string name="accessibility_sleep_timer_active_remaining">Activ, %1$s rămas</string>

[values-ru]
<string name="accessibility_sleep_timer_active_remaining">Активен, осталось %1$s</string>

[values-si]
<string name="accessibility_sleep_timer_active_remaining">සක්‍රියයි, %1$s ඉතිරිව ඇත</string>

[values-sk]
<string name="accessibility_sleep_timer_active_remaining">Aktívne, zostáva %1$s</string>

[values-sl]
<string name="accessibility_sleep_timer_active_remaining">Aktivno, preostane %1$s</string>

[values-sq]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, mbeten %1$s</string>

[values-sr]
<string name="accessibility_sleep_timer_active_remaining">Активан, преостало је %1$s</string>

[values-sv]
<string name="accessibility_sleep_timer_active_remaining">Aktiv, %1$s kvar</string>

[values-sw]
<string name="accessibility_sleep_timer_active_remaining">Inatumika, imesalia %1$s</string>

[values-ta]
<string name="accessibility_sleep_timer_active_remaining">செயலில் உள்ளது, %1$s மீதமுள்ளது</string>

[values-te]
<string name="accessibility_sleep_timer_active_remaining">సక్రియంగా ఉంది, %1$s మిగిలి ఉంది</string>

[values-th]
<string name="accessibility_sleep_timer_active_remaining">เปิดใช้งานอยู่ เหลือเวลา %1$s</string>

[values-tr]
<string name="accessibility_sleep_timer_active_remaining">Etkin, %1$s kaldı</string>

[values-uk]
<string name="accessibility_sleep_timer_active_remaining">Активно, залишилося %1$s</string>

[values-ur]
<string name="accessibility_sleep_timer_active_remaining">فعال، %1$s باقی ہے</string>

[values-uz]
<string name="accessibility_sleep_timer_active_remaining">Faol, %1$s qoldi</string>

[values-vi]
<string name="accessibility_sleep_timer_active_remaining">Đang bật, còn lại %1$s</string>

[values-xh]
<string name="accessibility_sleep_timer_active_remaining">Iyasebenza, %1$s eseleyo</string>

[values-yi]
<string name="accessibility_sleep_timer_active_remaining">אַקטיוו, %1$s פֿאַרבליבן</string>

[values-yo]
<string name="accessibility_sleep_timer_active_remaining">Ń ṣiṣẹ́, %1$s kù</string>

[values-zh-rCN]
<string name="accessibility_sleep_timer_active_remaining">已开启，剩余 %1$s</string>

[values-zh-rHK]
<string name="accessibility_sleep_timer_active_remaining">已開啟，剩餘 %1$s</string>

[values-zh-rTW]
<string name="accessibility_sleep_timer_active_remaining">已開啟，剩餘 %1$s</string>

[values-zu]
<string name="accessibility_sleep_timer_active_remaining">Iyasebenza, %1$s esele</string>
"""

root_dir = "C:/Users/bou/StudioProjects/AMARadio/app/src/main/res"

blocks = re.split(r'\[(values-[a-zA-Z0-9-]+)\]', translations_data)
updated_count = 0

for i in range(1, len(blocks), 2):
    folder_name = blocks[i]
    content = blocks[i+1].strip()

    xml_path = os.path.join(root_dir, folder_name, "strings.xml")
    if not os.path.exists(xml_path):
        print(f"Übersprungen (nicht gefunden): {xml_path}")
        continue

    with open(xml_path, "r", encoding="utf-8") as f:
        existing_xml = f.read()

    lines_to_add = []
    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        match = re.search(r'name="([^"]+)"', line)
        if match:
            key = match.group(1)
            pattern = re.compile(rf'^\s*<string name="{key}".*?</string>\s*\n?', re.MULTILINE | re.DOTALL)
            existing_xml = pattern.sub('', existing_xml)

            val_match = re.search(r'>([^<]+)<', line)
            if val_match:
                val = val_match.group(1)
                val_escaped = re.sub(r"(?<!\\)'", r"\'", val)
                line = line.replace(f">{val}<", f">{val_escaped}<")

            lines_to_add.append("    " + line)

    if "</resources>" in existing_xml:
        new_content = existing_xml.replace("</resources>", "\n".join(lines_to_add) + "\n</resources>")
    else:
        new_content = existing_xml + "\n" + "\n".join(lines_to_add)

    with open(xml_path, "w", encoding="utf-8") as f:
        f.write(new_content)
    updated_count += 1

print(f"Fertig: {updated_count} Dateien aktualisiert.")