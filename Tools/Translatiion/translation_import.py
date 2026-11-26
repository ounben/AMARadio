import os
import re

translations_data = """
[values-af]
<string name="accessibility_maximize_player">Maksimeer speler</string>
<string name="accessibility_minimize_player">Minimaliseer speler</string>
<string name="accessibility_reorder_station">Herrangskik %1$s</string>
<string name="action_move_down">Skuif af</string>
<string name="action_move_to_top">Skuif na bo</string>
<string name="action_move_up">Skuif op</string>
<string name="action_pause_recording">Onderbreek opname</string>
<string name="action_play_recording">Speel opname</string>
<string name="dialog_move_to_position_title">Skuif \"%1$s\" na posisie</string>
<string name="label_current_position">(huidige)</string>

[values-am]
<string name="accessibility_maximize_player">ማጫወቻውን አሳድግ</string>
<string name="accessibility_minimize_player">ማጫወቻውን አሳንስ</string>
<string name="accessibility_reorder_station">%1$s እንደገና ደርድር</string>
<string name="action_move_down">ወደ ታች ውሰድ</string>
<string name="action_move_to_top">ወደ ላይ ውሰድ</string>
<string name="action_move_up">ወደ ላይ ውሰድ</string>
<string name="action_pause_recording">ቀረጻውን ለአፍታ አቁም</string>
<string name="action_play_recording">ቀረጻውን አጫውት</string>
<string name="dialog_move_to_position_title">\"%1$s\" ወደ ቦታው ውሰድ</string>
<string name="label_current_position">(የአሁኑ)</string>

[values-ar]
<string name="accessibility_maximize_player">تكبير المشغل</string>
<string name="accessibility_minimize_player">تصغير المشغل</string>
<string name="accessibility_reorder_station">إعادة ترتيب %1$s</string>
<string name="action_move_down">تحريك لأسفل</string>
<string name="action_move_to_top">نقل إلى الأعلى</string>
<string name="action_move_up">تحريك لأعلى</string>
<string name="action_pause_recording">إيقاف التسجيل مؤقتًا</string>
<string name="action_play_recording">تشغيل التسجيل</string>
<string name="dialog_move_to_position_title">نقل \"%1$s\" إلى الموضع</string>
<string name="label_current_position">(الحالي)</string>

[values-as]
<string name="accessibility_maximize_player">প্লেয়াৰ ডাঙৰ কৰক</string>
<string name="accessibility_minimize_player">প্লেয়াৰ সৰু কৰক</string>
<string name="accessibility_reorder_station">%1$s পুনৰ সজাওক</string>
<string name="action_move_down">তললৈ নিয়াওক</string>
<string name="action_move_to_top">সৰ্বোচ্চ স্থানলৈ নিয়াওক</string>
<string name="action_move_up">ওপৰলৈ নিয়াওক</string>
<string name="action_pause_recording">ৰেকৰ্ডিং বিৰতি দিয়ক</string>
<string name="action_play_recording">ৰেকৰ্ডিং চলাওক</string>
<string name="dialog_move_to_position_title">\"%1$s\" স্থানলৈ নিয়াওক</string>
<string name="label_current_position">(বৰ্তমান)</string>

[values-az]
<string name="accessibility_maximize_player">Pleyeri böyüt</string>
<string name="accessibility_minimize_player">Pleyeri kiçilt</string>
<string name="accessibility_reorder_station">%1$s yenidən sırala</string>
<string name="action_move_down">Aşağı daşı</string>
<string name="action_move_to_top">Ən yuxarı daşı</string>
<string name="action_move_up">Yuxarı daşı</string>
<string name="action_pause_recording">Yazmanı fasiləyə qoy</string>
<string name="action_play_recording">Səsyazmanı oxut</string>
<string name="dialog_move_to_position_title">\"%1$s\" mövqeyinə köçür</string>
<string name="label_current_position">(cari)</string>

[values-be]
<string name="accessibility_maximize_player">Разгарнуць плэер</string>
<string name="accessibility_minimize_player">Згарнуць плэер</string>
<string name="accessibility_reorder_station">Змяніць парадак %1$s</string>
<string name="action_move_down">Перамясціць уніз</string>
<string name="action_move_to_top">Перамясціць у пачатак</string>
<string name="action_move_up">Перамясціць уверх</string>
<string name="action_pause_recording">Прыпыніць запіс</string>
<string name="action_play_recording">Прайграць запіс</string>
<string name="dialog_move_to_position_title">Перамясціць \"%1$s\" на пазіцыю</string>
<string name="label_current_position">(бягучая)</string>

[values-bg]
<string name="accessibility_maximize_player">Увеличаване на плейъра</string>
<string name="accessibility_minimize_player">Минимизиране на плейъра</string>
<string name="accessibility_reorder_station">Пренареждане на %1$s</string>
<string name="action_move_down">Премести надолу</string>
<string name="action_move_to_top">Премести най-отгоре</string>
<string name="action_move_up">Премести нагоре</string>
<string name="action_pause_recording">Пауза на записа</string>
<string name="action_play_recording">Възпроизвеждане на записа</string>
<string name="dialog_move_to_position_title">Преместване на \"%1$s\" на позиция</string>
<string name="label_current_position">(текуща)</string>

[values-bn]
<string name="accessibility_maximize_player">প্লেয়ার বড় করুন</string>
<string name="accessibility_minimize_player">প্লেয়ার ছোট করুন</string>
<string name="accessibility_reorder_station">%1$s পুনরায় সাজান</string>
<string name="action_move_down">নিচে সরান</string>
<string name="action_move_to_top">সবার উপরে সরান</string>
<string name="action_move_up">উপরে সরান</string>
<string name="action_pause_recording">রেকর্ডিং বিরতি দিন</string>
<string name="action_play_recording">রেকর্ডিং চালান</string>
<string name="dialog_move_to_position_title">\"%1$s\" অবস্থানে সরান</string>
<string name="label_current_position">(বর্তমান)</string>

[values-bs]
<string name="accessibility_maximize_player">Maksimiziraj plejer</string>
<string name="accessibility_minimize_player">Minimiziraj plejer</string>
<string name="accessibility_reorder_station">Promijeni redoslijed %1$s</string>
<string name="action_move_down">Pomjeri dolje</string>
<string name="action_move_to_top">Pomjeri na vrh</string>
<string name="action_move_up">Pomjeri gore</string>
<string name="action_pause_recording">Pauziraj snimanje</string>
<string name="action_play_recording">Pusti snimak</string>
<string name="dialog_move_to_position_title">Premjesti \"%1$s\" na poziciju</string>
<string name="label_current_position">(trenutna)</string>

[values-ca]
<string name="accessibility_maximize_player">Maximitza el reproductor</string>
<string name="accessibility_minimize_player">Minimitza el reproductor</string>
<string name="accessibility_reorder_station">Reordena %1$s</string>
<string name="action_move_down">Mou avall</string>
<string name="action_move_to_top">Mou a dalt de tot</string>
<string name="action_move_up">Mou amunt</string>
<string name="action_pause_recording">Posa en pausa la gravació</string>
<string name="action_play_recording">Reprodueix la gravació</string>
<string name="dialog_move_to_position_title">Mou \"%1$s\" a la posició</string>
<string name="label_current_position">(actual)</string>

[values-cs]
<string name="accessibility_maximize_player">Maximalizovat přehrávač</string>
<string name="accessibility_minimize_player">Minimalizovat přehrávač</string>
<string name="accessibility_reorder_station">Změnit pořadí %1$s</string>
<string name="action_move_down">Posunout dolů</string>
<string name="action_move_to_top">Přesunout nahoru</string>
<string name="action_move_up">Posunout nahoru</string>
<string name="action_pause_recording">Pozastavit nahrávání</string>
<string name="action_play_recording">Přehrát záznam</string>
<string name="dialog_move_to_position_title">Přesunout \"%1$s\" na pozici</string>
<string name="label_current_position">(aktuální)</string>

[values-da]
<string name="accessibility_maximize_player">Maksimer afspiller</string>
<string name="accessibility_minimize_player">Minimer afspiller</string>
<string name="accessibility_reorder_station">Omarranger %1$s</string>
<string name="action_move_down">Flyt ned</string>
<string name="action_move_to_top">Flyt til toppen</string>
<string name="action_move_up">Flyt op</string>
<string name="action_pause_recording">Sæt optagelse på pause</string>
<string name="action_play_recording">Afspil optagelse</string>
<string name="dialog_move_to_position_title">Flyt \"%1$s\" til position</string>
<string name="label_current_position">(nuværende)</string>

[values-de]
<string name="accessibility_maximize_player">Player maximieren</string>
<string name="accessibility_minimize_player">Player minimieren</string>
<string name="accessibility_reorder_station">%1$s neu anordnen</string>
<string name="action_move_down">Nach unten verschieben</string>
<string name="action_move_to_top">Ganz nach oben verschieben</string>
<string name="action_move_up">Nach oben verschieben</string>
<string name="action_pause_recording">Aufnahme pausieren</string>
<string name="action_play_recording">Aufnahme abspielen</string>
<string name="dialog_move_to_position_title">„%1$s“ an Position verschieben</string>
<string name="label_current_position">(aktuell)</string>

[values-el]
<string name="accessibility_maximize_player">Μεγιστοποίηση προγράμματος αναπαραγωγής</string>
<string name="accessibility_minimize_player">Ελαχιστοποίηση προγράμματος αναπαραγωγής</string>
<string name="accessibility_reorder_station">Αναδιάταξη %1$s</string>
<string name="action_move_down">Μετακίνηση προς τα κάτω</string>
<string name="action_move_to_top">Μετακίνηση στην κορυφή</string>
<string name="action_move_up">Μετακίνηση προς τα πάνω</string>
<string name="action_pause_recording">Παύση εγγραφής</string>
<string name="action_play_recording">Αναπαραγωγή εγγραφής</string>
<string name="dialog_move_to_position_title">Μετακίνηση \"%1$s\" στη θέση</string>
<string name="label_current_position">(τρέχουσα)</string>

[values-es]
<string name="accessibility_maximize_player">Maximizar reproductor</string>
<string name="accessibility_minimize_player">Minimizar reproductor</string>
<string name="accessibility_reorder_station">Reordenar %1$s</string>
<string name="action_move_down">Bajar</string>
<string name="action_move_to_top">Mover al principio</string>
<string name="action_move_up">Subir</string>
<string name="action_pause_recording">Pausar grabación</string>
<string name="action_play_recording">Reproducir grabación</string>
<string name="dialog_move_to_position_title">Mover \"%1$s\" a la posición</string>
<string name="label_current_position">(actual)</string>

[values-et]
<string name="accessibility_maximize_player">Maksimeeri pleier</string>
<string name="accessibility_minimize_player">Minimeeri pleier</string>
<string name="accessibility_reorder_station">Muuda %1$s järjekorda</string>
<string name="action_move_down">Liiguta alla</string>
<string name="action_move_to_top">Liiguta üles</string>
<string name="action_move_up">Liiguta ülespoole</string>
<string name="action_pause_recording">Peata salvestamine</string>
<string name="action_play_recording">Esita salvestus</string>
<string name="dialog_move_to_position_title">Teisalda \"%1$s\" asukohta</string>
<string name="label_current_position">(praegune)</string>

[values-eu]
<string name="accessibility_maximize_player">Handitu erreproduzigailua</string>
<string name="accessibility_minimize_player">Txikitu erreproduzigailua</string>
<string name="accessibility_reorder_station">Berrantolatu %1$s</string>
<string name="action_move_down">Mugitu behera</string>
<string name="action_move_to_top">Mugitu goialdera</string>
<string name="action_move_up">Mugitu gora</string>
<string name="action_pause_recording">Pausatu grabazioa</string>
<string name="action_play_recording">Erreproduzitu grabazioa</string>
<string name="dialog_move_to_position_title">Mugitu \"%1$s\" posiziora</string>
<string name="label_current_position">(oraingoa)</string>

[values-fa]
<string name="accessibility_maximize_player">بزرگ کردن پخش‌کننده</string>
<string name="accessibility_minimize_player">کوچک کردن پخش‌کننده</string>
<string name="accessibility_reorder_station">تغییر ترتیب %1$s</string>
<string name="action_move_down">انتقال به پایین</string>
<string name="action_move_to_top">انتقال به بالا</string>
<string name="action_move_up">انتقال به سمت بالا</string>
<string name="action_pause_recording">مکث ضبط</string>
<string name="action_play_recording">پخش ضبط</string>
<string name="dialog_move_to_position_title">انتقال \"%1$s\" به موقعیت</string>
<string name="label_current_position">(فعلی)</string>

[values-fi]
<string name="accessibility_maximize_player">Suurenna soitin</string>
<string name="accessibility_minimize_player">Pienennä soitin</string>
<string name="accessibility_reorder_station">Järjestä %1$s uudelleen</string>
<string name="action_move_down">Siirrä alas</string>
<string name="action_move_to_top">Siirrä ylimmäksi</string>
<string name="action_move_up">Siirrä ylös</string>
<string name="action_pause_recording">Keskeytä tallennus</string>
<string name="action_play_recording">Toista tallenne</string>
<string name="dialog_move_to_position_title">Siirrä \"%1$s\" sijaintiin</string>
<string name="label_current_position">(nykyinen)</string>

[values-fil]
<string name="accessibility_maximize_player">I-maximize ang player</string>
<string name="accessibility_minimize_player">I-minimize ang player</string>
<string name="accessibility_reorder_station">Muling ayusin ang %1$s</string>
<string name="action_move_down">Ilipat pababa</string>
<string name="action_move_to_top">Ilipat sa itaas</string>
<string name="action_move_up">Ilipat pataas</string>
<string name="action_pause_recording">I-pause ang pagre-record</string>
<string name="action_play_recording">I-play ang recording</string>
<string name="dialog_move_to_position_title">Ilipat ang \"%1$s\" sa posisyon</string>
<string name="label_current_position">(kasalukuyan)</string>

[values-fr]
<string name="accessibility_maximize_player">Agrandir le lecteur</string>
<string name="accessibility_minimize_player">Réduire le lecteur</string>
<string name="accessibility_reorder_station">Réorganiser %1$s</string>
<string name="action_move_down">Descendre</string>
<string name="action_move_to_top">Déplacer tout en haut</string>
<string name="action_move_up">Monter</string>
<string name="action_pause_recording">Mettre l\'enregistrement en pause</string>
<string name="action_play_recording">Lire l\'enregistrement</string>
<string name="dialog_move_to_position_title">Déplacer \"%1$s\" à la position</string>
<string name="label_current_position">(actuelle)</string>

[values-ga]
<string name="accessibility_maximize_player">Uasmhéadaigh an seinnteoir</string>
<string name="accessibility_minimize_player">Íoslaghdaigh an seinnteoir</string>
<string name="accessibility_reorder_station">Athordaigh %1$s</string>
<string name="action_move_down">Bog síos</string>
<string name="action_move_to_top">Bog go barr</string>
<string name="action_move_up">Bog suas</string>
<string name="action_pause_recording">Cuir an taifeadadh ar sos</string>
<string name="action_play_recording">Seinn taifeadadh</string>
<string name="dialog_move_to_position_title">Bog \"%1$s\" go dtí an suíomh</string>
<string name="label_current_position">(reatha)</string>

[values-gl]
<string name="accessibility_maximize_player">Maximizar o reprodutor</string>
<string name="accessibility_minimize_player">Minimizar o reprodutor</string>
<string name="accessibility_reorder_station">Reordenar %1$s</string>
<string name="action_move_down">Baixar</string>
<string name="action_move_to_top">Mover ao principio</string>
<string name="action_move_up">Subir</string>
<string name="action_pause_recording">Pausar a gravación</string>
<string name="action_play_recording">Reproducir gravación</string>
<string name="dialog_move_to_position_title">Mover \"%1$s\" á posición</string>
<string name="label_current_position">(actual)</string>

[values-gu]
<string name="accessibility_maximize_player">પ્લેયર મોટું કરો</string>
<string name="accessibility_minimize_player">પ્લેયર નાનું કરો</string>
<string name="accessibility_reorder_station">%1$s નો ક્રમ બદલો</string>
<string name="action_move_down">નીચે ખસેડો</string>
<string name="action_move_to_top">સૌથી ઉપર ખસેડો</string>
<string name="action_move_up">ઉપર ખસેડો</string>
<string name="action_pause_recording">રેકોર્ડિંગ થોભાવો</string>
<string name="action_play_recording">રેકોર્ડિંગ ચલાવો</string>
<string name="dialog_move_to_position_title">\"%1$s\" ને સ્થાન પર ખસેડો</string>
<string name="label_current_position">(વર્તમાન)</string>

[values-ha]
<string name="accessibility_maximize_player">Faɗaɗa mai kunnawa</string>
<string name="accessibility_minimize_player">Rage mai kunnawa</string>
<string name="accessibility_reorder_station">Sake tsara %1$s</string>
<string name="action_move_down">Matsa ƙasa</string>
<string name="action_move_to_top">Matsa zuwa sama</string>
<string name="action_move_up">Matsa sama</string>
<string name="action_pause_recording">Dakatar da rikodi</string>
<string name="action_play_recording">Kunna rikodi</string>
<string name="dialog_move_to_position_title">Matsar da \"%1$s\" zuwa matsayi</string>
<string name="label_current_position">(na yanzu)</string>

[values-he]
<string name="accessibility_maximize_player">הגדל נגן</string>
<string name="accessibility_minimize_player">מזער נגן</string>
<string name="accessibility_reorder_station">שנה סדר של %1$s</string>
<string name="action_move_down">הזז למטה</string>
<string name="action_move_to_top">העבר לראש הרשימה</string>
<string name="action_move_up">הזז למעלה</string>
<string name="action_pause_recording">השהה הקלטה</string>
<string name="action_play_recording">הפעל הקלטה</string>
<string name="dialog_move_to_position_title">הזז את \"%1$s\" למיקום</string>
<string name="label_current_position">(נוכחי)</string>

[values-hi]
<string name="accessibility_maximize_player">प्लेयर बड़ा करें</string>
<string name="accessibility_minimize_player">प्लेयर छोटा करें</string>
<string name="accessibility_reorder_station">%1$s का क्रम बदलें</string>
<string name="action_move_down">नीचे ले जाएं</string>
<string name="action_move_to_top">सबसे ऊपर ले जाएं</string>
<string name="action_move_up">ऊपर ले जाएं</string>
<string name="action_pause_recording">रिकॉर्डिंग रोकें</string>
<string name="action_play_recording">रिकॉर्डिंग चलाएं</string>
<string name="dialog_move_to_position_title">\"%1$s\" को स्थिति पर ले जाएं</string>
<string name="label_current_position">(वर्तमान)</string>

[values-hr]
<string name="accessibility_maximize_player">Maksimiziraj reproduktor</string>
<string name="accessibility_minimize_player">Minimiziraj reproduktor</string>
<string name="accessibility_reorder_station">Promijeni redoslijed %1$s</string>
<string name="action_move_down">Pomakni dolje</string>
<string name="action_move_to_top">Premjesti na vrh</string>
<string name="action_move_up">Pomakni gore</string>
<string name="action_pause_recording">Pauziraj snimanje</string>
<string name="action_play_recording">Reproduciraj snimku</string>
<string name="dialog_move_to_position_title">Premjesti \"%1$s\" na mjesto</string>
<string name="label_current_position">(trenutno)</string>

[values-hu]
<string name="accessibility_maximize_player">Lejátszó maximalizálása</string>
<string name="accessibility_minimize_player">Lejátszó minimalizálása</string>
<string name="accessibility_reorder_station">%1$s átrendezése</string>
<string name="action_move_down">Mozgatás lefelé</string>
<string name="action_move_to_top">Mozgatás legfelülre</string>
<string name="action_move_up">Mozgatás felfelé</string>
<string name="action_pause_recording">Felvétel szüneteltetése</string>
<string name="action_play_recording">Felvétel lejátszása</string>
<string name="dialog_move_to_position_title">\"%1$s\" áthelyezése pozícióra</string>
<string name="label_current_position">(jelenlegi)</string>

[values-hy]
<string name="accessibility_maximize_player">Մեծացնել նվագարկիչը</string>
<string name="accessibility_minimize_player">Փոքրացնել նվագարկիչը</string>
<string name="accessibility_reorder_station">Վերադասավորել %1$s-ը</string>
<string name="action_move_down">Տեղափոխել ներքև</string>
<string name="action_move_to_top">Տեղափոխել վերև</string>
<string name="action_move_up">Բարձրացնել վերև</string>
<string name="action_pause_recording">Դադարեցնել ձայնագրությունը</string>
<string name="action_play_recording">Նվագարկել ձայնագրությունը</string>
<string name="dialog_move_to_position_title">Տեղափոխել \"%1$s\" դիրք</string>
<string name="label_current_position">(ընթացիկ)</string>

[values-id]
<string name="accessibility_maximize_player">Maksimalkan pemutar</string>
<string name="accessibility_minimize_player">Minimalkan pemutar</string>
<string name="accessibility_reorder_station">Urutkan ulang %1$s</string>
<string name="action_move_down">Pindah ke bawah</string>
<string name="action_move_to_top">Pindah ke paling atas</string>
<string name="action_move_up">Pindah ke atas</string>
<string name="action_pause_recording">Jeda rekaman</string>
<string name="action_play_recording">Putar rekaman</string>
<string name="dialog_move_to_position_title">Pindahkan \"%1$s\" ke posisi</string>
<string name="label_current_position">(saat ini)</string>

[values-ig]
<string name="accessibility_maximize_player">Mee ka ọkpụkpọ buru ibu</string>
<string name="accessibility_minimize_player">Mee ka ọkpụkpọ dị ntakịrị</string>
<string name="accessibility_reorder_station">Hazigharịa %1$s</string>
<string name="action_move_down">Bugharịa ala</string>
<string name="action_move_to_top">Bugharịa n\'elu</string>
<string name="action_move_up">Bugharịa elu</string>
<string name="action_pause_recording">Kwụsịtụ ndekọ</string>
<string name="action_play_recording">Kpọọ ndekọ</string>
<string name="dialog_move_to_position_title">Bugharịa \"%1$s\" n\'ọkwa</string>
<string name="label_current_position">(ugbu a)</string>

[values-in]
<string name="accessibility_maximize_player">Maksimalkan pemutar</string>
<string name="accessibility_minimize_player">Minimalkan pemutar</string>
<string name="accessibility_reorder_station">Urutkan ulang %1$s</string>
<string name="action_move_down">Pindah ke bawah</string>
<string name="action_move_to_top">Pindah ke paling atas</string>
<string name="action_move_up">Pindah ke atas</string>
<string name="action_pause_recording">Jeda rekaman</string>
<string name="action_play_recording">Putar rekaman</string>
<string name="dialog_move_to_position_title">Pindahkan \"%1$s\" ke posisi</string>
<string name="label_current_position">(saat ini)</string>

[values-is]
<string name="accessibility_maximize_player">Stækka spilara</string>
<string name="accessibility_minimize_player">Minnka spilara</string>
<string name="accessibility_reorder_station">Endurraða %1$s</string>
<string name="action_move_down">Færa niður</string>
<string name="action_move_to_top">Færa efst</string>
<string name="action_move_up">Færa upp</string>
<string name="action_pause_recording">Gera hlé á upptöku</string>
<string name="action_play_recording">Spila upptöku</string>
<string name="dialog_move_to_position_title">Færa \"%1$s\" í stöðu</string>
<string name="label_current_position">(núverandi)</string>

[values-it]
<string name="accessibility_maximize_player">Ingrandisci lettore</string>
<string name="accessibility_minimize_player">Riduci a icona lettore</string>
<string name="accessibility_reorder_station">Riordina %1$s</string>
<string name="action_move_down">Sposta giù</string>
<string name="action_move_to_top">Sposta in cima</string>
<string name="action_move_up">Sposta su</string>
<string name="action_pause_recording">Metti in pausa la registrazione</string>
<string name="action_play_recording">Riproduci registrazione</string>
<string name="dialog_move_to_position_title">Sposta \"%1$s\" nella posizione</string>
<string name="label_current_position">(attuale)</string>

[values-iw]
<string name="accessibility_maximize_player">הגדל נגן</string>
<string name="accessibility_minimize_player">מזער נגן</string>
<string name="accessibility_reorder_station">שנה סדר של %1$s</string>
<string name="action_move_down">הזז למטה</string>
<string name="action_move_to_top">העבר לראש הרשימה</string>
<string name="action_move_up">הזז למעלה</string>
<string name="action_pause_recording">השהה הקלטה</string>
<string name="action_play_recording">הפעל הקלטה</string>
<string name="dialog_move_to_position_title">הזז את \"%1$s\" למיקום</string>
<string name="label_current_position">(נוכחי)</string>

[values-ja]
<string name="accessibility_maximize_player">プレーヤーを最大化</string>
<string name="accessibility_minimize_player">プレーヤーを最小化</string>
<string name="accessibility_reorder_station">%1$s を並べ替え</string>
<string name="action_move_down">下へ移動</string>
<string name="action_move_to_top">一番上へ移動</string>
<string name="action_move_up">上へ移動</string>
<string name="action_pause_recording">録音を一時停止</string>
<string name="action_play_recording">録音を再生</string>
<string name="dialog_move_to_position_title">「%1$s」を位置に移動</string>
<string name="label_current_position">(現在)</string>

[values-ji]
<string name="accessibility_maximize_player">פֿאַרגרעסערן שפּילער</string>
<string name="accessibility_minimize_player">פֿאַרקלענערן שפּילער</string>
<string name="accessibility_reorder_station">איבערסאָרטירן %1$s</string>
<string name="action_move_down">רוקן אַראָפּ</string>
<string name="action_move_to_top">רוקן צום שפּיץ</string>
<string name="action_move_up">רוקן אַרויף</string>
<string name="action_pause_recording">פּויזירן רעקאָרדירונג</string>
<string name="action_play_recording">שפּילן רעקאָרדירונג</string>
<string name="dialog_move_to_position_title">רוקן \"%1$s\" צו פּאָזיציע</string>
<string name="label_current_position">(איצטיק)</string>

[values-ka]
<string name="accessibility_maximize_player">დამკვრელის გაშლა</string>
<string name="accessibility_minimize_player">დამკვრელის ჩაკეცვა</string>
<string name="accessibility_reorder_station">%1$s-ის გადალაგება</string>
<string name="action_move_down">ქვემოთ გადატანა</string>
<string name="action_move_to_top">თავში გადატანა</string>
<string name="action_move_up">ზემოთ გადატანა</string>
<string name="action_pause_recording">ჩაწერის დაპაუზება</string>
<string name="action_play_recording">ჩანაწერის დაკვრა</string>
<string name="dialog_move_to_position_title">\"%1$s\"-ის გადატანა პოზიციაზე</string>
<string name="label_current_position">(მიმდინარე)</string>

[values-kk]
<string name="accessibility_maximize_player">Ойнатқышты жаю</string>
<string name="accessibility_minimize_player">Ойнатқышты жию</string>
<string name="accessibility_reorder_station">%1$s ретін өзгерту</string>
<string name="action_move_down">Төмен жылжыту</string>
<string name="action_move_to_top">Ең жоғарыға жылжыту</string>
<string name="action_move_up">Жоғары жылжыту</string>
<string name="action_pause_recording">Жазуды кідірту</string>
<string name="action_play_recording">Жазбаны ойнату</string>
<string name="dialog_move_to_position_title">\"%1$s\" орнына жылжыту</string>
<string name="label_current_position">(ағымдағы)</string>

[values-km]
<string name="accessibility_maximize_player">ពង្រីកកម្មវិធីចាក់</string>
<string name="accessibility_minimize_player">បង្រួមកម្មវិធីចាក់</string>
<string name="accessibility_reorder_station">រៀបចំលំដាប់ %1$s ឡើងវិញ</string>
<string name="action_move_down">ផ្លាស់ទីចុះក្រោម</string>
<string name="action_move_to_top">ផ្លាស់ទីទៅកំពូល</string>
<string name="action_move_up">ផ្លាស់ទីឡើងលើ</string>
<string name="action_pause_recording">ផ្អាកការថត</string>
<string name="action_play_recording">ចាក់ការថត</string>
<string name="dialog_move_to_position_title">ផ្លាស់ទី \"%1$s\" ទៅទីតាំង</string>
<string name="label_current_position">(បច្ចុប្បន្ន)</string>

[values-kn]
<string name="accessibility_maximize_player">ಪ್ಲೇಯರ್ ದೊಡ್ಡದಾಗಿಸಿ</string>
<string name="accessibility_minimize_player">ಪ್ಲೇಯರ್ ಚಿಕ್ಕದಾಗಿಸಿ</string>
<string name="accessibility_reorder_station">%1$s ಮರುಕ್ರಮಗೊಳಿಸಿ</string>
<string name="action_move_down">ಕೆಳಗೆ ಸರಿಸಿ</string>
<string name="action_move_to_top">ಮೇಲಕ್ಕೆ ಸರಿಸಿ</string>
<string name="action_move_up">ಮೇಲಕ್ಕೆ ಸರಿಸಿ</string>
<string name="action_pause_recording">ರೆಕಾರ್ಡಿಂಗ್ ವಿರಾಮಗೊಳಿಸಿ</string>
<string name="action_play_recording">ರೆಕಾರ್ಡಿಂಗ್ ಪ್ಲೇ ಮಾಡಿ</string>
<string name="dialog_move_to_position_title">\"%1$s\" ಅನ್ನು ಸ್ಥಾನಕ್ಕೆ ಸರಿಸಿ</string>
<string name="label_current_position">(ಪ್ರಸ್ತುತ)</string>

[values-ko]
<string name="accessibility_maximize_player">플레이어 최대화</string>
<string name="accessibility_minimize_player">플레이어 최소화</string>
<string name="accessibility_reorder_station">%1$s 순서 변경</string>
<string name="action_move_down">아래로 이동</string>
<string name="action_move_to_top">맨 위로 이동</string>
<string name="action_move_up">위로 이동</string>
<string name="action_pause_recording">녹음 일시정지</string>
<string name="action_play_recording">녹음 재생</string>
<string name="dialog_move_to_position_title">\"%1$s\" 위치 이동</string>
<string name="label_current_position">(현재)</string>

[values-ky]
<string name="accessibility_maximize_player">Ойноткучту чоңойтуу</string>
<string name="accessibility_minimize_player">Ойноткучту кичирейтүү</string>
<string name="accessibility_reorder_station">%1$s иретин өзгөртүү</string>
<string name="action_move_down">Төмөн жылдыруу</string>
<string name="action_move_to_top">Эң жогоруга жылдыруу</string>
<string name="action_move_up">Жогору жылдыруу</string>
<string name="action_pause_recording">Жаздырууну тындыруу</string>
<string name="action_play_recording">Жаздырууну ойнотуу</string>
<string name="dialog_move_to_position_title">\"%1$s\" абалына жылдыруу</string>
<string name="label_current_position">(учурдагы)</string>

[values-lo]
<string name="accessibility_maximize_player">ຂະຫຍາຍຕົວຫຼິ້ນ</string>
<string name="accessibility_minimize_player">ຫຍໍ້ຕົວຫຼິ້ນ</string>
<string name="accessibility_reorder_station">ຈັດລຽງ %1$s ໃໝ່</string>
<string name="action_move_down">ຍ້າຍລົງ</string>
<string name="action_move_to_top">ຍ້າຍໄປເທິງສຸດ</string>
<string name="action_move_up">ຍ້າຍຂຶ້ນ</string>
<string name="action_pause_recording">ຢຸດການບັນທຶກຊົ່ວຄາວ</string>
<string name="action_play_recording">ຫຼິ້ນການບັນທຶກ</string>
<string name="dialog_move_to_position_title">ຍ້າຍ \"%1$s\" ໄປຕຳແໜ່ງ</string>
<string name="label_current_position">(ປັດຈຸບັນ)</string>

[values-lt]
<string name="accessibility_maximize_player">Išskleisti grotuvą</string>
<string name="accessibility_minimize_player">Suskleisti grotuvą</string>
<string name="accessibility_reorder_station">Pertvarkyti %1$s</string>
<string name="action_move_down">Perkelti žemyn</string>
<string name="action_move_to_top">Perkelti į viršų</string>
<string name="action_move_up">Perkelti aukštyn</string>
<string name="action_pause_recording">Pristabdyti įrašymą</string>
<string name="action_play_recording">Leisti įrašą</string>
<string name="dialog_move_to_position_title">Perkelti \"%1$s\" į poziciją</string>
<string name="label_current_position">(dabartinė)</string>

[values-lv]
<string name="accessibility_maximize_player">Maksimizēt atskaņotāju</string>
<string name="accessibility_minimize_player">Minimizēt atskaņotāju</string>
<string name="accessibility_reorder_station">Pārkārtot %1$s</string>
<string name="action_move_down">Pārvietot uz leju</string>
<string name="action_move_to_top">Pārvietot uz augšu</string>
<string name="action_move_up">Pārvietot augšup</string>
<string name="action_pause_recording">Pauzēt ierakstīšanu</string>
<string name="action_play_recording">Atskaņot ierakstu</string>
<string name="dialog_move_to_position_title">Pārvietot \"%1$s\" uz pozīciju</string>
<string name="label_current_position">(pašreizējā)</string>

[values-mk]
<string name="accessibility_maximize_player">Зголеми го плеерот</string>
<string name="accessibility_minimize_player">Намали го плеерот</string>
<string name="accessibility_reorder_station">Промени редослед на %1$s</string>
<string name="action_move_down">Премести надолу</string>
<string name="action_move_to_top">Премести на врв</string>
<string name="action_move_up">Премести нагоре</string>
<string name="action_pause_recording">Паузирај снимање</string>
<string name="action_play_recording">Пушти снимање</string>
<string name="dialog_move_to_position_title">Премести \"%1$s\" на позиција</string>
<string name="label_current_position">(тековна)</string>

[values-ml]
<string name="accessibility_maximize_player">പ്ലെയർ വലുതാക്കുക</string>
<string name="accessibility_minimize_player">പ്ലെയർ ചെറുതാക്കുക</string>
<string name="accessibility_reorder_station">%1$s പുനഃക്രമീകരിക്കുക</string>
<string name="action_move_down">താഴേക്ക് നീക്കുക</string>
<string name="action_move_to_top">ഏറ്റവും മുകളിലേക്ക് നീക്കുക</string>
<string name="action_move_up">മുകളിലേക്ക് നീക്കുക</string>
<string name="action_pause_recording">റെക്കോർഡിംഗ് താൽക്കാലികമായി നിർത്തുക</string>
<string name="action_play_recording">റെക്കോർഡിംഗ് പ്ലേ ചെയ്യുക</string>
<string name="dialog_move_to_position_title">\"%1$s\" സ്ഥാനത്തേക്ക് നീക്കുക</string>
<string name="label_current_position">(നിലവിലെ)</string>

[values-mn]
<string name="accessibility_maximize_player">Тоглуулагчийг томруулах</string>
<string name="accessibility_minimize_player">Тоглуулагчийг жижигрүүлэх</string>
<string name="accessibility_reorder_station">%1$s дарааллыг өөрчлөх</string>
<string name="action_move_down">Доош зөөх</string>
<string name="action_move_to_top">Хамгийн дээр зөөх</string>
<string name="action_move_up">Дээш зөөх</string>
<string name="action_pause_recording">Бичлэгийг түр зогсоох</string>
<string name="action_play_recording">Бичлэг тоглуулах</string>
<string name="dialog_move_to_position_title">\"%1$s\"-г байрлал руу зөөх</string>
<string name="label_current_position">(одоогийн)</string>

[values-mr]
<string name="accessibility_maximize_player">प्लेअर मोठा करा</string>
<string name="accessibility_minimize_player">प्लेअर लहान करा</string>
<string name="accessibility_reorder_station">%1$s चा क्रम बदला</string>
<string name="action_move_down">खाली हलवा</string>
<string name="action_move_to_top">सर्वात वर हलवा</string>
<string name="action_move_up">वर हलवा</string>
<string name="action_pause_recording">रेकॉर्डिंग थांबवा</string>
<string name="action_play_recording">रेकॉर्डिंग प्ले करा</string>
<string name="dialog_move_to_position_title">\"%1$s\" ला स्थानावर हलवा</string>
<string name="label_current_position">(सध्याचे)</string>

[values-ms]
<string name="accessibility_maximize_player">Maksimumkan pemain media</string>
<string name="accessibility_minimize_player">Minimumkan pemain media</string>
<string name="accessibility_reorder_station">Susun semula %1$s</string>
<string name="action_move_down">Alih ke bawah</string>
<string name="action_move_to_top">Alih ke paling atas</string>
<string name="action_move_up">Alih ke atas</string>
<string name="action_pause_recording">Jeda rakaman</string>
<string name="action_play_recording">Mainkan rakaman</string>
<string name="dialog_move_to_position_title">Alihkan \"%1$s\" ke kedudukan</string>
<string name="label_current_position">(semasa)</string>

[values-mt]
<string name="accessibility_maximize_player">Immassimizza l-plejer</string>
<string name="accessibility_minimize_player">Imminimizza l-plejer</string>
<string name="accessibility_reorder_station">Irranġa mill-ġdid %1$s</string>
<string name="action_move_down">Mexxi \'l isfel</string>
<string name="action_move_to_top">Mexxi fil-quċċata</string>
<string name="action_move_up">Mexxi \'l fuq</string>
<string name="action_pause_recording">Issospendi r-reġistrazzjoni</string>
<string name="action_play_recording">Ilgħab ir-reġistrazzjoni</string>
<string name="dialog_move_to_position_title">Mexxi \"%1$s\" għall-pożizzjoni</string>
<string name="label_current_position">(attwali)</string>

[values-my]
<string name="accessibility_maximize_player">ပလေယာကို ချဲ့ရန်</string>
<string name="accessibility_minimize_player">ပလေယာကို ချုံ့ရန်</string>
<string name="accessibility_reorder_station">%1$s ကို ပြန်စီရန်</string>
<string name="action_move_down">အောက်သို့ရွှေ့ရန်</string>
<string name="action_move_to_top">ထိပ်ဆုံးသို့ရွှေ့ရန်</string>
<string name="action_move_up">အပေါ်သို့ရွှေ့ရန်</string>
<string name="action_pause_recording">အသံဖမ်းခြင်း ခဏရပ်ရန်</string>
<string name="action_play_recording">အသံဖမ်းမှု ဖွင့်ရန်</string>
<string name="dialog_move_to_position_title">\"%1$s\" ကို နေရာသို့ ရွှေ့ရန်</string>
<string name="label_current_position">(လက်ရှိ)</string>

[values-nb]
<string name="accessibility_maximize_player">Maksimer avspiller</string>
<string name="accessibility_minimize_player">Minimer avspiller</string>
<string name="accessibility_reorder_station">Endre rekkefølge på %1$s</string>
<string name="action_move_down">Flytt ned</string>
<string name="action_move_to_top">Flytt øverst</string>
<string name="action_move_up">Flytt opp</string>
<string name="action_pause_recording">Sett opptak på pause</string>
<string name="action_play_recording">Spill av opptak</string>
<string name="dialog_move_to_position_title">Flytt \"%1$s\" til posisjon</string>
<string name="label_current_position">(nåværende)</string>

[values-ne]
<string name="accessibility_maximize_player">प्लेयर ठूलो बनाउनुहोस्</string>
<string name="accessibility_minimize_player">प्लेयर सानो बनाउनुहोस्</string>
<string name="accessibility_reorder_station">%1$s को क्रम मिलाउनुहोस्</string>
<string name="action_move_down">तल सार्नुहोस्</string>
<string name="action_move_to_top">सबैभन्दा माथि सार्नुहोस्</string>
<string name="action_move_up">माथि सार्नुहोस्</string>
<string name="action_pause_recording">रेकर्डिङ पज गर्नुहोस्</string>
<string name="action_play_recording">रेकर्डिङ प्ले गर्नुहोस्</string>
<string name="dialog_move_to_position_title">\"%1$s\" लाई स्थानमा सार्नुहोस्</string>
<string name="label_current_position">(हालको)</string>

[values-nl]
<string name="accessibility_maximize_player">Speler maximaliseren</string>
<string name="accessibility_minimize_player">Speler minimaliseren</string>
<string name="accessibility_reorder_station">Volgorde van %1$s wijzigen</string>
<string name="action_move_down">Omlaag verplaatsen</string>
<string name="action_move_to_top">Naar boven verplaatsen</string>
<string name="action_move_up">Omhoog verplaatsen</string>
<string name="action_pause_recording">Opname pauzeren</string>
<string name="action_play_recording">Opname afspelen</string>
<string name="dialog_move_to_position_title">\"%1$s\" naar positie verplaatsen</string>
<string name="label_current_position">(huidig)</string>

[values-nn]
<string name="accessibility_maximize_player">Maksimer avspelar</string>
<string name="accessibility_minimize_player">Minimer avspelar</string>
<string name="accessibility_reorder_station">Endre rekkjefølgje på %1$s</string>
<string name="action_move_down">Flytt ned</string>
<string name="action_move_to_top">Flytt øvst</string>
<string name="action_move_up">Flytt opp</string>
<string name="action_pause_recording">Set opptak på pause</string>
<string name="action_play_recording">Spel av opptak</string>
<string name="dialog_move_to_position_title">Flytt \"%1$s\" til posisjon</string>
<string name="label_current_position">(noverande)</string>

[values-or]
<string name="accessibility_maximize_player">ପ୍ଲେୟାର ବଡ଼ କରନ୍ତୁ</string>
<string name="accessibility_minimize_player">ପ୍ଲେୟାର ଛୋଟ କରନ୍ତୁ</string>
<string name="accessibility_reorder_station">%1$s ପୁନଃସଜାନ୍ତୁ</string>
<string name="action_move_down">ତଳକୁ ନିଅନ୍ତୁ</string>
<string name="action_move_to_top">ସବୁଠାରୁ ଉପରକୁ ନିଅନ୍ତୁ</string>
<string name="action_move_up">ଉପରକୁ ନିଅନ୍ତୁ</string>
<string name="action_pause_recording">ରେକର୍ଡିଂ ବିରତି ଦିଅନ୍ତୁ</string>
<string name="action_play_recording">ରେକର୍ଡିଂ ଚଲାନ୍ତୁ</string>
<string name="dialog_move_to_position_title">\"%1$s\" କୁ ସ୍ଥାନକୁ ନିଅନ୍ତୁ</string>
<string name="label_current_position">(ବର୍ତ୍ତମାନ)</string>

[values-pa]
<string name="accessibility_maximize_player">ਪਲੇਅਰ ਵੱਡਾ ਕਰੋ</string>
<string name="accessibility_minimize_player">ਪਲੇਅਰ ਛੋਟਾ ਕਰੋ</string>
<string name="accessibility_reorder_station">%1$s ਦਾ ਕ੍ਰਮ ਬਦਲੋ</string>
<string name="action_move_down">ਹੇਠਾਂ ਭੇਜੋ</string>
<string name="action_move_to_top">ਸਭ ਤੋਂ ਉੱਪਰ ਭੇਜੋ</string>
<string name="action_move_up">ਉੱਪਰ ਭੇਜੋ</string>
<string name="action_pause_recording">ਰਿਕਾਰਡਿੰਗ ਰੋਕੋ</string>
<string name="action_play_recording">ਰਿਕਾਰਡਿੰਗ ਚਲਾਓ</string>
<string name="dialog_move_to_position_title">\"%1$s\" ਨੂੰ ਸਥਿਤੀ \'ਤੇ ਭੇਜੋ</string>
<string name="label_current_position">(ਮੌਜੂਦਾ)</string>

[values-pl]
<string name="accessibility_maximize_player">Maksymalizuj odtwarzacz</string>
<string name="accessibility_minimize_player">Minimalizuj odtwarzacz</string>
<string name="accessibility_reorder_station">Zmień kolejność %1$s</string>
<string name="action_move_down">Przenieś w dół</string>
<string name="action_move_to_top">Przenieś na górę</string>
<string name="action_move_up">Przenieś w górę</string>
<string name="action_pause_recording">Wstrzymaj nagrywanie</string>
<string name="action_play_recording">Odtwórz nagranie</string>
<string name="dialog_move_to_position_title">Przenieś „%1$s” na pozycję</string>
<string name="label_current_position">(aktualna)</string>

[values-ps]
<string name="accessibility_maximize_player">غږوونکی لوی کړئ</string>
<string name="accessibility_minimize_player">غږوونکی کوچنی کړئ</string>
<string name="accessibility_reorder_station">د %1$s ترتیب بدل کړئ</string>
<string name="action_move_down">لاندې یوسئ</string>
<string name="action_move_to_top">تر ټولو پورته یوسئ</string>
<string name="action_move_up">پورته یوسئ</string>
<string name="action_pause_recording">ثبتول وځنډوئ</string>
<string name="action_play_recording">ثبت شوی غږ واورئ</string>
<string name="dialog_move_to_position_title">\"%1$s\" ځای ته یوسئ</string>
<string name="label_current_position">(اوسنی)</string>

[values-pt]
<string name="accessibility_maximize_player">Maximizar leitor</string>
<string name="accessibility_minimize_player">Minimizar leitor</string>
<string name="accessibility_reorder_station">Reordenar %1$s</string>
<string name="action_move_down">Mover para baixo</string>
<string name="action_move_to_top">Mover para o topo</string>
<string name="action_move_up">Mover para cima</string>
<string name="action_pause_recording">Pausar gravação</string>
<string name="action_play_recording">Reproduzir gravação</string>
<string name="dialog_move_to_position_title">Mover \"%1$s\" para a posição</string>
<string name="label_current_position">(atual)</string>

[values-pt-rBR]
<string name="accessibility_maximize_player">Maximizar reprodutor</string>
<string name="accessibility_minimize_player">Minimizar reprodutor</string>
<string name="accessibility_reorder_station">Reordenar %1$s</string>
<string name="action_move_down">Mover para baixo</string>
<string name="action_move_to_top">Mover para o topo</string>
<string name="action_move_up">Mover para cima</string>
<string name="action_pause_recording">Pausar gravação</string>
<string name="action_play_recording">Reproduzir gravação</string>
<string name="dialog_move_to_position_title">Mover \"%1$s\" para a posição</string>
<string name="label_current_position">(atual)</string>

[values-rm]
<string name="accessibility_maximize_player">Maximizar il player</string>
<string name="accessibility_minimize_player">Minimar il player</string>
<string name="accessibility_reorder_station">Midar la successiun da %1$s</string>
<string name="action_move_down">Spustar engiu</string>
<string name="action_move_to_top">Spustar tut ensi</string>
<string name="action_move_up">Spustar ensi</string>
<string name="action_pause_recording">Pausar la registraziun</string>
<string name="action_play_recording">Far ir la registraziun</string>
<string name="dialog_move_to_position_title">Spustar «%1$s» a la posiziun</string>
<string name="label_current_position">(actual)</string>

[values-ro]
<string name="accessibility_maximize_player">Maximizează playerul</string>
<string name="accessibility_minimize_player">Minimizează playerul</string>
<string name="accessibility_reorder_station">Reordonează %1$s</string>
<string name="action_move_down">Mută în jos</string>
<string name="action_move_to_top">Mută sus de tot</string>
<string name="action_move_up">Mută în sus</string>
<string name="action_pause_recording">Pune înregistrarea pe pauză</string>
<string name="action_play_recording">Redă înregistrarea</string>
<string name="dialog_move_to_position_title">Mută \"%1$s\" pe poziția</string>
<string name="label_current_position">(actuală)</string>

[values-ru]
<string name="accessibility_maximize_player">Развернуть плеер</string>
<string name="accessibility_minimize_player">Свернуть плеер</string>
<string name="accessibility_reorder_station">Изменить порядок %1$s</string>
<string name="action_move_down">Переместить вниз</string>
<string name="action_move_to_top">Переместить в начало</string>
<string name="action_move_up">Переместить вверх</string>
<string name="action_pause_recording">Приостановить запись</string>
<string name="action_play_recording">Воспроизвести запись</string>
<string name="dialog_move_to_position_title">Переместить \"%1$s\" на позицию</string>
<string name="label_current_position">(текущая)</string>

[values-si]
<string name="accessibility_maximize_player">ප්ලේයරය විශාල කරන්න</string>
<string name="accessibility_minimize_player">ප්ලේයරය කුඩා කරන්න</string>
<string name="accessibility_reorder_station">%1$s නැවත පෙළගස්වන්න</string>
<string name="action_move_down">පහළට ගෙන යන්න</string>
<string name="action_move_to_top">ඉහළටම ගෙන යන්න</string>
<string name="action_move_up">ඉහළට ගෙන යන්න</string>
<string name="action_pause_recording">පටිගත කිරීම විරාම කරන්න</string>
<string name="action_play_recording">පටිගත කිරීම වාදනය කරන්න</string>
<string name="dialog_move_to_position_title">\"%1$s\" ස්ථානයට ගෙන යන්න</string>
<string name="label_current_position">(වත්මන්)</string>

[values-sk]
<string name="accessibility_maximize_player">Maximalizovať prehrávač</string>
<string name="accessibility_minimize_player">Minimalizovať prehrávač</string>
<string name="accessibility_reorder_station">Zmeniť poradie %1$s</string>
<string name="action_move_down">Posunúť nadol</string>
<string name="action_move_to_top">Presunúť na začiatok</string>
<string name="action_move_up">Posunúť nahor</string>
<string name="action_pause_recording">Pozastaviť nahrávanie</string>
<string name="action_play_recording">Prehrať nahrávku</string>
<string name="dialog_move_to_position_title">Presunúť \"%1$s\" na pozíciu</string>
<string name="label_current_position">(aktuálna)</string>

[values-sl]
<string name="accessibility_maximize_player">Maksimiraj predvajalnik</string>
<string name="accessibility_minimize_player">Minimiraj predvajalnik</string>
<string name="accessibility_reorder_station">Preuredi %1$s</string>
<string name="action_move_down">Premakni navzdol</string>
<string name="action_move_to_top">Premakni na vrh</string>
<string name="action_move_up">Premakni navzgor</string>
<string name="action_pause_recording">Začasno ustavi snemanje</string>
<string name="action_play_recording">Predvajaj posnetek</string>
<string name="dialog_move_to_position_title">Premakni \"%1$s\" na položaj</string>
<string name="label_current_position">(trenutni)</string>

[values-sq]
<string name="accessibility_maximize_player">Maksimizo luajtësin</string>
<string name="accessibility_minimize_player">Minimizo luajtësin</string>
<string name="accessibility_reorder_station">Rirendit %1$s</string>
<string name="action_move_down">Lëviz poshtë</string>
<string name="action_move_to_top">Lëviz në krye</string>
<string name="action_move_up">Lëviz lart</string>
<string name="action_pause_recording">Vendos regjistrimin në pauzë</string>
<string name="action_play_recording">Luaj regjistrimin</string>
<string name="dialog_move_to_position_title">Zhvendos \"%1$s\" në pozicion</string>
<string name="label_current_position">(aktual)</string>

[values-sr]
<string name="accessibility_maximize_player">Максимизирај плејер</string>
<string name="accessibility_minimize_player">Минимизирај плејер</string>
<string name="accessibility_reorder_station">Промени редослед %1$s</string>
<string name="action_move_down">Помери надоле</string>
<string name="action_move_to_top">Помери на врх</string>
<string name="action_move_up">Помери нагоре</string>
<string name="action_pause_recording">Паузирај снимање</string>
<string name="action_play_recording">Пусти снимак</string>
<string name="dialog_move_to_position_title">Помери \"%1$s\" на позицију</string>
<string name="label_current_position">(тренутна)</string>

[values-sv]
<string name="accessibility_maximize_player">Maximera spelare</string>
<string name="accessibility_minimize_player">Minimera spelare</string>
<string name="accessibility_reorder_station">Ändra ordning på %1$s</string>
<string name="action_move_down">Flytta ner</string>
<string name="action_move_to_top">Flytta överst</string>
<string name="action_move_up">Flytta upp</string>
<string name="action_pause_recording">Pausa inspelning</string>
<string name="action_play_recording">Spela upp inspelning</string>
<string name="dialog_move_to_position_title">Flytta \"%1$s\" till position</string>
<string name="label_current_position">(nuvarande)</string>

[values-sw]
<string name="accessibility_maximize_player">Kuza kichezaji</string>
<string name="accessibility_minimize_player">Punguza kichezaji</string>
<string name="accessibility_reorder_station">Panga upya %1$s</string>
<string name="action_move_down">Sogeza chini</string>
<string name="action_move_to_top">Sogeza juu kabisa</string>
<string name="action_move_up">Sogeza juu</string>
<string name="action_pause_recording">Sitisha kurekodi</string>
<string name="action_play_recording">Cheza rekodi</string>
<string name="dialog_move_to_position_title">Sogeza \"%1$s\" kwenye nafasi</string>
<string name="label_current_position">(ya sasa)</string>

[values-ta]
<string name="accessibility_maximize_player">பிளேயரைப் பெரிதாக்கு</string>
<string name="accessibility_minimize_player">பிளேயரைச் சிறிதாக்கு</string>
<string name="accessibility_reorder_station">%1$s வரிசையை மாற்று</string>
<string name="action_move_down">கீழே நகர்த்து</string>
<string name="action_move_to_top">மேலே நகர்த்து</string>
<string name="action_move_up">மேல்நோக்கி நகர்த்து</string>
<string name="action_pause_recording">பதிவை இடைநிறுத்து</string>
<string name="action_play_recording">பதிவை இயக்கு</string>
<string name="dialog_move_to_position_title">\"%1$s\" ஐ நிலைக்கு நகர்த்து</string>
<string name="label_current_position">(தற்போதைய)</string>

[values-te]
<string name="accessibility_maximize_player">ప్లేయర్‌ను పెద్దది చేయండి</string>
<string name="accessibility_minimize_player">ప్లేయర్‌ను చిన్నది చేయండి</string>
<string name="accessibility_reorder_station">%1$s క్రమాన్ని మార్చండి</string>
<string name="action_move_down">క్రిందికి జరపండి</string>
<string name="action_move_to_top">అగ్రభాగానికి జరపండి</string>
<string name="action_move_up">పైకి జరపండి</string>
<string name="action_pause_recording">రికార్డింగ్‌ను పాజ్ చేయండి</string>
<string name="action_play_recording">రికార్డింగ్ ప్లే చేయండి</string>
<string name="dialog_move_to_position_title">\"%1$s\" ని స్థానానికి జరపండి</string>
<string name="label_current_position">(ప్రస్తుత)</string>

[values-th]
<string name="accessibility_maximize_player">ขยายเครื่องเล่น</string>
<string name="accessibility_minimize_player">ย่อเครื่องเล่น</string>
<string name="accessibility_reorder_station">จัดลำดับ %1$s ใหม่</string>
<string name="action_move_down">เลื่อนลง</string>
<string name="action_move_to_top">ย้ายไปบนสุด</string>
<string name="action_move_up">เลื่อนขึ้น</string>
<string name="action_pause_recording">หยุดการบันทึกชั่วคราว</string>
<string name="action_play_recording">เล่นการบันทึก</string>
<string name="dialog_move_to_position_title">ย้าย \"%1$s\" ไปยังตำแหน่ง</string>
<string name="label_current_position">(ปัจจุบัน)</string>

[values-tr]
<string name="accessibility_maximize_player">Oynatıcıyı büyüt</string>
<string name="accessibility_minimize_player">Oynatıcıyı küçült</string>
<string name="accessibility_reorder_station">%1$s öğesini yeniden sırala</string>
<string name="action_move_down">Aşağı taşı</string>
<string name="action_move_to_top">En üste taşı</string>
<string name="action_move_up">Yukarı taşı</string>
<string name="action_pause_recording">Kaydı duraklat</string>
<string name="action_play_recording">Kaydı oynat</string>
<string name="dialog_move_to_position_title">\"%1$s\" öğesini konuma taşı</string>
<string name="label_current_position">(mevcut)</string>

[values-uk]
<string name="accessibility_maximize_player">Розгорнути плеєр</string>
<string name="accessibility_minimize_player">Згорнути плеєр</string>
<string name="accessibility_reorder_station">Змінити порядок %1$s</string>
<string name="action_move_down">Перемістити вниз</string>
<string name="action_move_to_top">Перемістити на початок</string>
<string name="action_move_up">Перемістити вгору</string>
<string name="action_pause_recording">Призупинити запис</string>
<string name="action_play_recording">Відтворити запис</string>
<string name="dialog_move_to_position_title">Перемістити \"%1$s\" на позицію</string>
<string name="label_current_position">(поточна)</string>

[values-ur]
<string name="accessibility_maximize_player">پلیئر کو بڑا کریں</string>
<string name="accessibility_minimize_player">پلیئر کو چھوٹا کریں</string>
<string name="accessibility_reorder_station">%1$s کی ترتیب تبدیل کریں</string>
<string name="action_move_down">نیچے منتقل کریں</string>
<string name="action_move_to_top">سب سے اوپر منتقل کریں</string>
<string name="action_move_up">اوپر منتقل کریں</string>
<string name="action_pause_recording">ریکارڈنگ روکیں</string>
<string name="action_play_recording">ریکارڈنگ چلائیں</string>
<string name="dialog_move_to_position_title">\"%1$s\" کو پوزیشن پر منتقل کریں</string>
<string name="label_current_position">(موجودہ)</string>

[values-uz]
<string name="accessibility_maximize_player">Pleyerni kattalashtirish</string>
<string name="accessibility_minimize_player">Pleyerni kichraytirish</string>
<string name="accessibility_reorder_station">%1$s tartibini o‘zgartirish</string>
<string name="action_move_down">Pastga siljitish</string>
<string name="action_move_to_top">Eng yuqoriga o‘tkazish</string>
<string name="action_move_up">Yuqoriga siljitish</string>
<string name="action_pause_recording">Yozishni pauza qilish</string>
<string name="action_play_recording">Yozuvni ijro etish</string>
<string name="dialog_move_to_position_title">\"%1$s\" ni pozitsiyaga o‘tkazish</string>
<string name="label_current_position">(joriy)</string>

[values-vi]
<string name="accessibility_maximize_player">Phóng to trình phát</string>
<string name="accessibility_minimize_player">Thu nhỏ trình phát</string>
<string name="accessibility_reorder_station">Sắp xếp lại %1$s</string>
<string name="action_move_down">Di chuyển xuống</string>
<string name="action_move_to_top">Di chuyển lên đầu</string>
<string name="action_move_up">Di chuyển lên</string>
<string name="action_pause_recording">Tạm dừng ghi</string>
<string name="action_play_recording">Phát bản ghi</string>
<string name="dialog_move_to_position_title">Di chuyển \"%1$s\" đến vị trí</string>
<string name="label_current_position">(hiện tại)</string>

[values-xh]
<string name="accessibility_maximize_player">Yandisa isidlali</string>
<string name="accessibility_minimize_player">Nciphisa isidlali</string>
<string name="accessibility_reorder_station">Hlela kwakhona i-%1$s</string>
<string name="action_move_down">Hambisa ezantsi</string>
<string name="action_move_to_top">Hambisa phezulu</string>
<string name="action_move_up">Nyusela phezulu</string>
<string name="action_pause_recording">Misa okwethutyana ukurekhoda</string>
<string name="action_play_recording">Dlala ukurekhoda</string>
<string name="dialog_move_to_position_title">Hambisa \"%1$s\" kwindawo</string>
<string name="label_current_position">(yangoku)</string>

[values-yi]
<string name="accessibility_maximize_player">פֿאַרגרעסערן שפּילער</string>
<string name="accessibility_minimize_player">פֿאַרקלענערן שפּילער</string>
<string name="accessibility_reorder_station">איבערסאָרטירן %1$s</string>
<string name="action_move_down">רוקן אַראָפּ</string>
<string name="action_move_to_top">רוקן צום שפּיץ</string>
<string name="action_move_up">רוקן אַרויף</string>
<string name="action_pause_recording">פּויזירן רעקאָרדירונג</string>
<string name="action_play_recording">שפּילן רעקאָרדירונג</string>
<string name="dialog_move_to_position_title">רוקן \"%1$s\" צו פּאָזיציע</string>
<string name="label_current_position">(איצטיק)</string>

[values-yo]
<string name="accessibility_maximize_player">Fẹ ẹrọ orin</string>
<string name="accessibility_minimize_player">Din ẹrọ orin ku</string>
<string name="accessibility_reorder_station">Tun %1$s to</string>
<string name="action_move_down">Gbe si isalẹ</string>
<string name="action_move_to_top">Gbe si oke patapata</string>
<string name="action_move_up">Gbe soke</string>
<string name="action_pause_recording">Da gbigbasilẹ duro diẹ</string>
<string name="action_play_recording">Mu gbigbasilẹ ṣiṣẹ</string>
<string name="dialog_move_to_position_title">Gbe \"%1$s\" lọ si ipo</string>
<string name="label_current_position">(lọwọlọwọ)</string>

[values-zh-rCN]
<string name="accessibility_maximize_player">最大化播放器</string>
<string name="accessibility_minimize_player">最小化播放器</string>
<string name="accessibility_reorder_station">重新排列 %1$s</string>
<string name="action_move_down">下移</string>
<string name="action_move_to_top">移至顶部</string>
<string name="action_move_up">上移</string>
<string name="action_pause_recording">暂停录音</string>
<string name="action_play_recording">播放录音</string>
<string name="dialog_move_to_position_title">将“%1$s”移至位置</string>
<string name="label_current_position">（当前）</string>

[values-zh-rHK]
<string name="accessibility_maximize_player">最大化播放器</string>
<string name="accessibility_minimize_player">最小化播放器</string>
<string name="accessibility_reorder_station">重新排列 %1$s</string>
<string name="action_move_down">下移</string>
<string name="action_move_to_top">移至頂部</string>
<string name="action_move_up">上移</string>
<string name="action_pause_recording">暫停錄音</string>
<string name="action_play_recording">播放錄音</string>
<string name="dialog_move_to_position_title">將「%1$s」移至位置</string>
<string name="label_current_position">（目前）</string>

[values-zh-rTW]
<string name="accessibility_maximize_player">最大化播放器</string>
<string name="accessibility_minimize_player">最小化播放器</string>
<string name="accessibility_reorder_station">重新排列 %1$s</string>
<string name="action_move_down">下移</string>
<string name="action_move_to_top">移至頂部</string>
<string name="action_move_up">上移</string>
<string name="action_pause_recording">暫停錄音</string>
<string name="action_play_recording">播放錄音</string>
<string name="dialog_move_to_position_title">將「%1$s」移至位置</string>
<string name="label_current_position">（目前）</string>

[values-zu]
<string name="accessibility_maximize_player">Khulisa isidlali</string>
<string name="accessibility_minimize_player">Nciphisa isidlali</string>
<string name="accessibility_reorder_station">Hlela kabusha i-%1$s</string>
<string name="action_move_down">Hambisa phansi</string>
<string name="action_move_to_top">Hambisa phezulu</string>
<string name="action_move_up">Hambisa phezulu</string>
<string name="action_pause_recording">Misa isikhashana ukurekhoda</string>
<string name="action_play_recording">Dlala ukurekhoda</string>
<string name="dialog_move_to_position_title">Hambisa \"%1$s\" endaweni</string>
<string name="label_current_position">(okwamanje)</string>
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