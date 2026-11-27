import os
import re

translations_data = """
[values-af]
<string name="accessibility_filter_date_from">Vanaf datum: %1$s</string>
<string name="accessibility_filter_date_to">Tot datum: %1$s</string>
<string name="accessibility_filter_station">Stasiefilter: %1$s</string>
<string name="accessibility_playback_speed">Terugspeelspoed %1$s</string>
<string name="accessibility_seek_slider">Terugspeelposisie</string>
<string name="accessibility_time_position">%1$s van %2$s</string>
<string name="action_change_playback_speed">Verander terugspeelspoed</string>
<string name="action_clear_date_filter">Vee datumfilter uit</string>
<string name="action_clear_station_filter">Vee stasiefilter uit</string>
<string name="action_forward_15s">15 sekondes vorentoe</string>
<string name="action_reset_playback_speed">Stel terugspeelspoed terug na 1x</string>
<string name="action_rewind_15s">15 sekondes terug</string>
<string name="action_scroll_to_top">Rol na bo</string>
<string name="dialog_replace_recording_message">\"%1$s\" word tans opgeneem. Wil jy die huidige opname stop en stoor en begin om \"%2$s\" op te neem?</string>
<string name="dialog_replace_recording_title">Opname aan die gang</string>
<string name="filter_all_stations">Alle stasies</string>
<string name="filter_date_any">Enige datum</string>

[values-am]
<string name="accessibility_filter_date_from">ከቀን፦ %1$s</string>
<string name="accessibility_filter_date_to">እስከ ቀን፦ %1$s</string>
<string name="accessibility_filter_station">የጣቢያ ማጣሪያ፦ %1$s</string>
<string name="accessibility_playback_speed">የመልሶ ማጫወት ፍጥነት %1$s</string>
<string name="accessibility_seek_slider">የመልሶ ማጫወት ቦታ</string>
<string name="accessibility_time_position">%1$s ከ %2$s</string>
<string name="action_change_playback_speed">የመልሶ ማጫወት ፍጥነት ቀይር</string>
<string name="action_clear_date_filter">የቀን ማጣሪያ አጽዳ</string>
<string name="action_clear_station_filter">የጣቢያ ማጣሪያ አጽዳ</string>
<string name="action_forward_15s">15 ሰከንድ ወደፊት</string>
<string name="action_reset_playback_speed">የመልሶ ማጫወት ፍጥነት ወደ 1x ዳግም አስጀምር</string>
<string name="action_rewind_15s">15 ሰከንድ ወደኋላ</string>
<string name="action_scroll_to_top">ወደ ላይ ሸብልል</string>
<string name="dialog_replace_recording_message">\"%1$s\" በአሁኑ ጊዜ እየተቀዳ ነው። የአሁኑን ቀረጻ አቁመህ ማስቀመጥ እና \"%2$s\" መቅዳት መጀመር ትፈልጋለህ?</string>
<string name="dialog_replace_recording_title">ቀረጻ በሂደት ላይ</string>
<string name="filter_all_stations">ሁሉም ጣቢያዎች</string>
<string name="filter_date_any">ማንኛውም ቀን</string>

[values-ar]
<string name="accessibility_filter_date_from">من تاريخ: %1$s</string>
<string name="accessibility_filter_date_to">إلى تاريخ: %1$s</string>
<string name="accessibility_filter_station">فلتر المحطة: %1$s</string>
<string name="accessibility_playback_speed">سرعة التشغيل %1$s</string>
<string name="accessibility_seek_slider">موضع التشغيل</string>
<string name="accessibility_time_position">%1$s من %2$s</string>
<string name="action_change_playback_speed">تغيير سرعة التشغيل</string>
<string name="action_clear_date_filter">مسح فلتر التاريخ</string>
<string name="action_clear_station_filter">مسح فلتر المحطة</string>
<string name="action_forward_15s">تقديم 15 ثانية</string>
<string name="action_reset_playback_speed">إعادة ضبط سرعة التشغيل إلى 1x</string>
<string name="action_rewind_15s">ترجيع 15 ثانية</string>
<string name="action_scroll_to_top">التمرير إلى الأعلى</string>
<string name="dialog_replace_recording_message">يتم حاليًا تسجيل \"%1$s\". هل تريد إيقاف التسجيل الحالي وحفظه وبدء تسجيل \"%2$s\"؟</string>
<string name="dialog_replace_recording_title">التسجيل قيد التقدم</string>
<string name="filter_all_stations">جميع المحطات</string>
<string name="filter_date_any">أي تاريخ</string>

[values-as]
<string name="accessibility_filter_date_from">তাৰিখৰ পৰা: %1$s</string>
<string name="accessibility_filter_date_to">তাৰিখলৈ: %1$s</string>
<string name="accessibility_filter_station">ষ্টেচন ফিল্টাৰ: %1$s</string>
<string name="accessibility_playback_speed">প্লেবেক গতি %1$s</string>
<string name="accessibility_seek_slider">প্লেবেক অৱস্থান</string>
<string name="accessibility_time_position">%2$s ৰ %1$s</string>
<string name="action_change_playback_speed">প্লেবেক গতি সলনি কৰক</string>
<string name="action_clear_date_filter">তাৰিখ ফিল্টাৰ মচক</string>
<string name="action_clear_station_filter">ষ্টেচন ফিল্টাৰ মচক</string>
<string name="action_forward_15s">১৫ ছেকেণ্ড আগুৱাই যাওক</string>
<string name="action_reset_playback_speed">প্লেবেক গতি 1x লৈ ৰিছেট কৰক</string>
<string name="action_rewind_15s">১৫ ছেকেণ্ড পিছুৱাই যাওক</string>
<string name="action_scroll_to_top">ওপৰলৈ স্ক্ৰল কৰক</string>
<string name="dialog_replace_recording_message">\"%1$s\" বৰ্তমান ৰেকৰ্ড হৈ আছে। আপুনি বৰ্তমানৰ ৰেকৰ্ডিং বন্ধ কৰি সংৰক্ষণ কৰিব বিচাৰে আৰু \"%2$s\" ৰেকৰ্ডিং আৰম্ভ কৰিব বিচাৰে নেকি?</string>
<string name="dialog_replace_recording_title">ৰেকৰ্ডিং চলি আছে</string>
<string name="filter_all_stations">সকলো ষ্টেচন</string>
<string name="filter_date_any">যিকোনো তাৰিখ</string>

[values-az]
<string name="accessibility_filter_date_from">Başlanğıc tarixi: %1$s</string>
<string name="accessibility_filter_date_to">Bitmə tarixi: %1$s</string>
<string name="accessibility_filter_station">Stansiya filtri: %1$s</string>
<string name="accessibility_playback_speed">Oxutma sürəti %1$s</string>
<string name="accessibility_seek_slider">Oxutma mövqeyi</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Oxutma sürətini dəyiş</string>
<string name="action_clear_date_filter">Tarix filtrini təmizlə</string>
<string name="action_clear_station_filter">Stansiya filtrini təmizlə</string>
<string name="action_forward_15s">15 saniyə irəli</string>
<string name="action_reset_playback_speed">Oxutma sürətini 1x-ə sıfırla</string>
<string name="action_rewind_15s">15 saniyə geri</string>
<string name="action_scroll_to_top">Yuxarı sürüşdür</string>
<string name="dialog_replace_recording_message">\"%1$s\" hazırda yazılır. Cari yazmanı dayandırıb saxlamaq və \"%2$s\" yazmağa başlamaq istəyirsiniz?</string>
<string name="dialog_replace_recording_title">Yazma davam edir</string>
<string name="filter_all_stations">Bütün stansiyalar</string>
<string name="filter_date_any">İstənilən tarix</string>

[values-be]
<string name="accessibility_filter_date_from">З даты: %1$s</string>
<string name="accessibility_filter_date_to">Па дату: %1$s</string>
<string name="accessibility_filter_station">Фільтр станцыі: %1$s</string>
<string name="accessibility_playback_speed">Хуткасць прайгравання %1$s</string>
<string name="accessibility_seek_slider">Пазіцыя прайгравання</string>
<string name="accessibility_time_position">%1$s з %2$s</string>
<string name="action_change_playback_speed">Змяніць хуткасць прайгравання</string>
<string name="action_clear_date_filter">Ачысціць фільтр даты</string>
<string name="action_clear_station_filter">Ачысціць фільтр станцыі</string>
<string name="action_forward_15s">Наперад на 15 секунд</string>
<string name="action_reset_playback_speed">Скінуць хуткасць прайгравання да 1x</string>
<string name="action_rewind_15s">Назад на 15 секунд</string>
<string name="action_scroll_to_top">Пракруціць уверх</string>
<string name="dialog_replace_recording_message">\"%1$s\" зараз запісваецца. Спыніць і захаваць бягучы запіс і пачаць запіс \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Ідзе запіс</string>
<string name="filter_all_stations">Усе станцыі</string>
<string name="filter_date_any">Любая дата</string>

[values-bg]
<string name="accessibility_filter_date_from">От дата: %1$s</string>
<string name="accessibility_filter_date_to">До дата: %1$s</string>
<string name="accessibility_filter_station">Филтър по станция: %1$s</string>
<string name="accessibility_playback_speed">Скорост на възпроизвеждане %1$s</string>
<string name="accessibility_seek_slider">Позиция на възпроизвеждане</string>
<string name="accessibility_time_position">%1$s от %2$s</string>
<string name="action_change_playback_speed">Промяна на скоростта на възпроизвеждане</string>
<string name="action_clear_date_filter">Изчистване на филтъра за дата</string>
<string name="action_clear_station_filter">Изчистване на филтъра за станция</string>
<string name="action_forward_15s">15 секунди напред</string>
<string name="action_reset_playback_speed">Нулиране на скоростта на възпроизвеждане на 1x</string>
<string name="action_rewind_15s">15 секунди назад</string>
<string name="action_scroll_to_top">Превъртане най-отгоре</string>
<string name="dialog_replace_recording_message">В момента се записва \"%1$s\". Искате ли да спрете и запазите текущия запис и да започнете запис на \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Записът е в ход</string>
<string name="filter_all_stations">Всички станции</string>
<string name="filter_date_any">Всяка дата</string>

[values-bn]
<string name="accessibility_filter_date_from">তারিখ থেকে: %1$s</string>
<string name="accessibility_filter_date_to">তারিখ পর্যন্ত: %1$s</string>
<string name="accessibility_filter_station">স্টেশন ফিল্টার: %1$s</string>
<string name="accessibility_playback_speed">প্লেব্যাকের গতি %1$s</string>
<string name="accessibility_seek_slider">প্লেব্যাকের অবস্থান</string>
<string name="accessibility_time_position">%2$s এর %1$s</string>
<string name="action_change_playback_speed">প্লেব্যাকের গতি পরিবর্তন করুন</string>
<string name="action_clear_date_filter">তারিখ ফিল্টার মুছুন</string>
<string name="action_clear_station_filter">স্টেশন ফিল্টার মুছুন</string>
<string name="action_forward_15s">১৫ সেকেন্ড সামনে</string>
<string name="action_reset_playback_speed">প্লেব্যাকের গতি 1x-এ রিসেট করুন</string>
<string name="action_rewind_15s">১৫ সেকেন্ড পিছনে</string>
<string name="action_scroll_to_top">সবার উপরে স্ক্রল করুন</string>
<string name="dialog_replace_recording_message">\"%1$s\" বর্তমানে রেকর্ড হচ্ছে। আপনি কি বর্তমান রেকর্ডিং বন্ধ ও সংরক্ষণ করে \"%2$s\" রেকর্ড শুরু করতে চান?</string>
<string name="dialog_replace_recording_title">রেকর্ডিং চলছে</string>
<string name="filter_all_stations">সব স্টেশন</string>
<string name="filter_date_any">যেকোনো তারিখ</string>

[values-bs]
<string name="accessibility_filter_date_from">Od datuma: %1$s</string>
<string name="accessibility_filter_date_to">Do datuma: %1$s</string>
<string name="accessibility_filter_station">Filter stanice: %1$s</string>
<string name="accessibility_playback_speed">Brzina reprodukcije %1$s</string>
<string name="accessibility_seek_slider">Pozicija reprodukcije</string>
<string name="accessibility_time_position">%1$s od %2$s</string>
<string name="action_change_playback_speed">Promijeni brzinu reprodukcije</string>
<string name="action_clear_date_filter">Obriši filter datuma</string>
<string name="action_clear_station_filter">Obriši filter stanice</string>
<string name="action_forward_15s">Naprijed 15 sekundi</string>
<string name="action_reset_playback_speed">Vrati brzinu reprodukcije na 1x</string>
<string name="action_rewind_15s">Nazad 15 sekundi</string>
<string name="action_scroll_to_top">Pomjeri na vrh</string>
<string name="dialog_replace_recording_message">\"%1$s\" se trenutno snima. Želite li zaustaviti i sačuvati trenutni snimak i započeti snimanje \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Snimanje u toku</string>
<string name="filter_all_stations">Sve stanice</string>
<string name="filter_date_any">Bilo koji datum</string>

[values-ca]
<string name="accessibility_filter_date_from">Des de la data: %1$s</string>
<string name="accessibility_filter_date_to">Fins a la data: %1$s</string>
<string name="accessibility_filter_station">Filtre d\'emissora: %1$s</string>
<string name="accessibility_playback_speed">Velocitat de reproducció %1$s</string>
<string name="accessibility_seek_slider">Posició de reproducció</string>
<string name="accessibility_time_position">%1$s de %2$s</string>
<string name="action_change_playback_speed">Canvia la velocitat de reproducció</string>
<string name="action_clear_date_filter">Esborra el filtre de data</string>
<string name="action_clear_station_filter">Esborra el filtre d\'emissora</string>
<string name="action_forward_15s">Avança 15 segons</string>
<string name="action_reset_playback_speed">Restableix la velocitat de reproducció a 1x</string>
<string name="action_rewind_15s">Rebobina 15 segons</string>
<string name="action_scroll_to_top">Desplaça\'t a dalt de tot</string>
<string name="dialog_replace_recording_message">Actualment s\'està gravant \"%1$s\". Vols aturar i desar la gravació actual i començar a gravar \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Gravació en curs</string>
<string name="filter_all_stations">Totes les emissores</string>
<string name="filter_date_any">Qualsevol data</string>

[values-cs]
<string name="accessibility_filter_date_from">Od data: %1$s</string>
<string name="accessibility_filter_date_to">Do data: %1$s</string>
<string name="accessibility_filter_station">Filtr stanic: %1$s</string>
<string name="accessibility_playback_speed">Rychlost přehrávání %1$s</string>
<string name="accessibility_seek_slider">Pozice přehrávání</string>
<string name="accessibility_time_position">%1$s z %2$s</string>
<string name="action_change_playback_speed">Změnit rychlost přehrávání</string>
<string name="action_clear_date_filter">Vymazat filtr data</string>
<string name="action_clear_station_filter">Vymazat filtr stanic</string>
<string name="action_forward_15s">Vpřed o 15 sekund</string>
<string name="action_reset_playback_speed">Obnovit rychlost přehrávání na 1x</string>
<string name="action_rewind_15s">Zpět o 15 sekund</string>
<string name="action_scroll_to_top">Posunout nahoru</string>
<string name="dialog_replace_recording_message">Stanice \"%1$s\" se právě nahrává. Chcete zastavit a uložit aktuální nahrávku a spustit nahrávání \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Probíhá nahrávání</string>
<string name="filter_all_stations">Všechny stanice</string>
<string name="filter_date_any">Jakékoliv datum</string>

[values-da]
<string name="accessibility_filter_date_from">Fra dato: %1$s</string>
<string name="accessibility_filter_date_to">Til dato: %1$s</string>
<string name="accessibility_filter_station">Stationsfilter: %1$s</string>
<string name="accessibility_playback_speed">Afspilningshastighed %1$s</string>
<string name="accessibility_seek_slider">Afspilningsposition</string>
<string name="accessibility_time_position">%1$s af %2$s</string>
<string name="action_change_playback_speed">Skift afspilningshastighed</string>
<string name="action_clear_date_filter">Ryd datofilter</string>
<string name="action_clear_station_filter">Ryd stationsfilter</string>
<string name="action_forward_15s">15 sekunder frem</string>
<string name="action_reset_playback_speed">Nulstil afspilningshastighed til 1x</string>
<string name="action_rewind_15s">15 sekunder tilbage</string>
<string name="action_scroll_to_top">Rul til toppen</string>
<string name="dialog_replace_recording_message">\"%1$s\" optages i øjeblikket. Vil du stoppe og gemme den nuværende optagelse og starte optagelse af \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Optagelse i gang</string>
<string name="filter_all_stations">Alle stationer</string>
<string name="filter_date_any">Enhver dato</string>

[values-de]
<string name="accessibility_filter_date_from">Von Datum: %1$s</string>
<string name="accessibility_filter_date_to">Bis Datum: %1$s</string>
<string name="accessibility_filter_station">Senderfilter: %1$s</string>
<string name="accessibility_playback_speed">Wiedergabegeschwindigkeit %1$s</string>
<string name="accessibility_seek_slider">Wiedergabeposition</string>
<string name="accessibility_time_position">%1$s von %2$s</string>
<string name="action_change_playback_speed">Wiedergabegeschwindigkeit ändern</string>
<string name="action_clear_date_filter">Datumsfilter löschen</string>
<string name="action_clear_station_filter">Senderfilter löschen</string>
<string name="action_forward_15s">15 Sekunden vorspulen</string>
<string name="action_reset_playback_speed">Wiedergabegeschwindigkeit auf 1x zurücksetzen</string>
<string name="action_rewind_15s">15 Sekunden zurückspulen</string>
<string name="action_scroll_to_top">Nach oben scrollen</string>
<string name="dialog_replace_recording_message">„%1$s“ wird derzeit aufgenommen. Möchten Sie die aktuelle Aufnahme stoppen und speichern und die Aufnahme von „%2$s“ starten?</string>
<string name="dialog_replace_recording_title">Aufnahme läuft</string>
<string name="filter_all_stations">Alle Sender</string>
<string name="filter_date_any">Beliebiges Datum</string>

[values-el]
<string name="accessibility_filter_date_from">Από ημερομηνία: %1$s</string>
<string name="accessibility_filter_date_to">Έως ημερομηνία: %1$s</string>
<string name="accessibility_filter_station">Φίλτρο σταθμού: %1$s</string>
<string name="accessibility_playback_speed">Ταχύτητα αναπαραγωγής %1$s</string>
<string name="accessibility_seek_slider">Θέση αναπαραγωγής</string>
<string name="accessibility_time_position">%1$s από %2$s</string>
<string name="action_change_playback_speed">Αλλαγή ταχύτητας αναπαραγωγής</string>
<string name="action_clear_date_filter">Εκκαθάριση φίλτρου ημερομηνίας</string>
<string name="action_clear_station_filter">Εκκαθάριση φίλτρου σταθμού</string>
<string name="action_forward_15s">15 δευτερόλεπτα μπροστά</string>
<string name="action_reset_playback_speed">Επαναφορά ταχύτητας αναπαραγωγής σε 1x</string>
<string name="action_rewind_15s">15 δευτερόλεπτα πίσω</string>
<string name="action_scroll_to_top">Κύλιση στην κορυφή</string>
<string name="dialog_replace_recording_message">Το \"%1$s\" εγγράφεται αυτήν τη στιγμή. Θέλετε να διακόψετε και να αποθηκεύσετε την τρέχουσα εγγραφή και να ξεκινήσετε την εγγραφή του \"%2$s\";</string>
<string name="dialog_replace_recording_title">Εγγραφή σε εξέλιξη</string>
<string name="filter_all_stations">Όλοι οι σταθμοί</string>
<string name="filter_date_any">Οποιαδήποτε ημερομηνία</string>

[values-es]
<string name="accessibility_filter_date_from">Desde la fecha: %1$s</string>
<string name="accessibility_filter_date_to">Hasta la fecha: %1$s</string>
<string name="accessibility_filter_station">Filtro de emisora: %1$s</string>
<string name="accessibility_playback_speed">Velocidad de reproducción %1$s</string>
<string name="accessibility_seek_slider">Posición de reproducción</string>
<string name="accessibility_time_position">%1$s de %2$s</string>
<string name="action_change_playback_speed">Cambiar velocidad de reproducción</string>
<string name="action_clear_date_filter">Borrar filtro de fecha</string>
<string name="action_clear_station_filter">Borrar filtro de emisora</string>
<string name="action_forward_15s">Adelantar 15 segundos</string>
<string name="action_reset_playback_speed">Restablecer velocidad de reproducción a 1x</string>
<string name="action_rewind_15s">Retroceder 15 segundos</string>
<string name="action_scroll_to_top">Desplazarse hacia arriba</string>
<string name="dialog_replace_recording_message">\"%1$s\" se está grabando actualmente. ¿Desea detener y guardar la grabación actual y comenzar a grabar \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Grabación en curso</string>
<string name="filter_all_stations">Todas las emisoras</string>
<string name="filter_date_any">Cualquier fecha</string>

[values-et]
<string name="accessibility_filter_date_from">Alates kuupäevast: %1$s</string>
<string name="accessibility_filter_date_to">Kuni kuupäevani: %1$s</string>
<string name="accessibility_filter_station">Jaama filter: %1$s</string>
<string name="accessibility_playback_speed">Taasesituse kiirus %1$s</string>
<string name="accessibility_seek_slider">Taasesituse asukoht</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Muuda taasesituse kiirust</string>
<string name="action_clear_date_filter">Tühjenda kuupäevafilter</string>
<string name="action_clear_station_filter">Tühjenda jaamafilter</string>
<string name="action_forward_15s">15 sekundit edasi</string>
<string name="action_reset_playback_speed">Lähtesta taasesituse kiirus 1x peale</string>
<string name="action_rewind_15s">15 sekundit tagasi</string>
<string name="action_scroll_to_top">Keri üles</string>
<string name="dialog_replace_recording_message">\"%1$s\" salvestamine on pooleli. Kas soovite praeguse salvestuse peatada ja salvestada ning alustada \"%2$s\" salvestamist?</string>
<string name="dialog_replace_recording_title">Salvestamine on pooleli</string>
<string name="filter_all_stations">Kõik jaamad</string>
<string name="filter_date_any">Mis tahes kuupäev</string>

[values-eu]
<string name="accessibility_filter_date_from">Hasiera-data: %1$s</string>
<string name="accessibility_filter_date_to">Amaiera-data: %1$s</string>
<string name="accessibility_filter_station">Irrati-iragazkia: %1$s</string>
<string name="accessibility_playback_speed">Erreprodukzio-abiadura %1$s</string>
<string name="accessibility_seek_slider">Erreprodukzio-posizioa</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Aldatu erreprodukzio-abiadura</string>
<string name="action_clear_date_filter">Garbitu data-iragazkia</string>
<string name="action_clear_station_filter">Garbitu irrati-iragazkia</string>
<string name="action_forward_15s">Aurreratu 15 segundo</string>
<string name="action_reset_playback_speed">Berrezarri erreprodukzio-abiadura 1x-era</string>
<string name="action_rewind_15s">Atzeratu 15 segundo</string>
<string name="action_scroll_to_top">Joan goialdera</string>
<string name="dialog_replace_recording_message">\"%1$s\" grabatzen ari da une honetan. Uneko grabazioa gelditu eta gorde nahi duzu, eta \"%2$s\" grabatzen hasi?</string>
<string name="dialog_replace_recording_title">Grabazioa abian</string>
<string name="filter_all_stations">Irrati guztiak</string>
<string name="filter_date_any">Edozein data</string>

[values-fa]
<string name="accessibility_filter_date_from">از تاریخ: %1$s</string>
<string name="accessibility_filter_date_to">تا تاریخ: %1$s</string>
<string name="accessibility_filter_station">فیلتر ایستگاه: %1$s</string>
<string name="accessibility_playback_speed">سرعت پخش %1$s</string>
<string name="accessibility_seek_slider">موقعیت پخش</string>
<string name="accessibility_time_position">%1$s از %2$s</string>
<string name="action_change_playback_speed">تغییر سرعت پخش</string>
<string name="action_clear_date_filter">پاک کردن فیلتر تاریخ</string>
<string name="action_clear_station_filter">پاک کردن فیلتر ایستگاه</string>
<string name="action_forward_15s">۱۵ ثانیه به جلو</string>
<string name="action_reset_playback_speed">بازنشانی سرعت پخش به 1x</string>
<string name="action_rewind_15s">۱۵ ثانیه به عقب</string>
<string name="action_scroll_to_top">اسکرول به بالا</string>
<string name="dialog_replace_recording_message">«%1$s» در حال حاضر در حال ضبط است. آیا می‌خواهید ضبط فعلی را متوقف و ذخیره کنید و ضبط «%2$s» را شروع کنید؟</string>
<string name="dialog_replace_recording_title">ضبط در حال انجام است</string>
<string name="filter_all_stations">همه ایستگاه‌ها</string>
<string name="filter_date_any">هر تاریخی</string>

[values-fi]
<string name="accessibility_filter_date_from">Alkaen päivämäärästä: %1$s</string>
<string name="accessibility_filter_date_to">Päättyen päivämäärään: %1$s</string>
<string name="accessibility_filter_station">Asemasuodatin: %1$s</string>
<string name="accessibility_playback_speed">Toistonopeus %1$s</string>
<string name="accessibility_seek_slider">Toistokohta</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Vaihda toistonopeutta</string>
<string name="action_clear_date_filter">Tyhjennä päivämääräsuodatin</string>
<string name="action_clear_station_filter">Tyhjennä asemasuodatin</string>
<string name="action_forward_15s">15 sekuntia eteenpäin</string>
<string name="action_reset_playback_speed">Palauta toistonopeus 1x:ksi</string>
<string name="action_rewind_15s">15 sekuntia taaksepäin</string>
<string name="action_scroll_to_top">Vieritä ylös</string>
<string name="dialog_replace_recording_message">\"%1$s\" on parhaillaan tallenteilla. Haluatko lopettaa ja tallentaa nykyisen tallenteen ja aloittaa kohteen \"%2$s\" tallentamisen?</string>
<string name="dialog_replace_recording_title">Tallennus käynnissä</string>
<string name="filter_all_stations">Kaikki asemat</string>
<string name="filter_date_any">Mikä tahansa päivämäärä</string>

[values-fil]
<string name="accessibility_filter_date_from">Mula sa petsa: %1$s</string>
<string name="accessibility_filter_date_to">Hanggang sa petsa: %1$s</string>
<string name="accessibility_filter_station">Filter ng istasyon: %1$s</string>
<string name="accessibility_playback_speed">Bilis ng playback %1$s</string>
<string name="accessibility_seek_slider">Posisyon ng playback</string>
<string name="accessibility_time_position">%1$s ng %2$s</string>
<string name="action_change_playback_speed">Baguhin ang bilis ng playback</string>
<string name="action_clear_date_filter">I-clear ang filter ng petsa</string>
<string name="action_clear_station_filter">I-clear ang filter ng istasyon</string>
<string name="action_forward_15s">I-forward nang 15 segundo</string>
<string name="action_reset_playback_speed">I-reset ang bilis ng playback sa 1x</string>
<string name="action_rewind_15s">I-rewind nang 15 segundo</string>
<string name="action_scroll_to_top">Mag-scroll sa itaas</string>
<string name="dialog_replace_recording_message">Kasalukuyang nire-record ang \"%1$s\". Gusto mo bang ihinto at i-save ang kasalukuyang recording at simulan ang pag-record ng \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Kasalukuyang nagre-record</string>
<string name="filter_all_stations">Lahat ng istasyon</string>
<string name="filter_date_any">Kahit anong petsa</string>

[values-fr]
<string name="accessibility_filter_date_from">À partir du : %1$s</string>
<string name="accessibility_filter_date_to">Jusqu\'au : %1$s</string>
<string name="accessibility_filter_station">Filtre de station : %1$s</string>
<string name="accessibility_playback_speed">Vitesse de lecture %1$s</string>
<string name="accessibility_seek_slider">Position de lecture</string>
<string name="accessibility_time_position">%1$s sur %2$s</string>
<string name="action_change_playback_speed">Modifier la vitesse de lecture</string>
<string name="action_clear_date_filter">Effacer le filtre de date</string>
<string name="action_clear_station_filter">Effacer le filtre de station</string>
<string name="action_forward_15s">Avancer de 15 secondes</string>
<string name="action_reset_playback_speed">Réinitialiser la vitesse de lecture à 1x</string>
<string name="action_rewind_15s">Reculer de 15 secondes</string>
<string name="action_scroll_to_top">Faire défiler vers le haut</string>
<string name="dialog_replace_recording_message">« %1$s » est en cours d\'enregistrement. Voulez-vous arrêter et enregistrer la capture actuelle, puis démarrer l\'enregistrement de « %2$s » ?</string>
<string name="dialog_replace_recording_title">Enregistrement en cours</string>
<string name="filter_all_stations">Toutes les stations</string>
<string name="filter_date_any">N\'importe quelle date</string>

[values-ga]
<string name="accessibility_filter_date_from">Ón dáta: %1$s</string>
<string name="accessibility_filter_date_to">Go dtí an dáta: %1$s</string>
<string name="accessibility_filter_station">Scagaire stáisiúin: %1$s</string>
<string name="accessibility_playback_speed">Luas athsheinm %1$s</string>
<string name="accessibility_seek_slider">Suíomh athsheinm</string>
<string name="accessibility_time_position">%1$s as %2$s</string>
<string name="action_change_playback_speed">Athraigh luas athsheinm</string>
<string name="action_clear_date_filter">Glan scagaire dáta</string>
<string name="action_clear_station_filter">Glan scagaire stáisiúin</string>
<string name="action_forward_15s">Ar aghaidh 15 soicind</string>
<string name="action_reset_playback_speed">Athshocraigh luas athsheinm go 1x</string>
<string name="action_rewind_15s">Siar 15 soicind</string>
<string name="action_scroll_to_top">Scrollaigh go barr</string>
<string name="dialog_replace_recording_message">Tá \"%1$s\" á thaifeadadh faoi láthair. Ar mhaith leat an taifeadadh reatha a stopadh agus a shábháil agus tosú ag taifeadadh \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Taifeadadh ar siúl</string>
<string name="filter_all_stations">Gach stáisiún</string>
<string name="filter_date_any">Aon dáta</string>

[values-gl]
<string name="accessibility_filter_date_from">Dende a data: %1$s</string>
<string name="accessibility_filter_date_to">Ata a data: %1$s</string>
<string name="accessibility_filter_station">Filtro de emisora: %1$s</string>
<string name="accessibility_playback_speed">Velocidade de reprodución %1$s</string>
<string name="accessibility_seek_slider">Posición de reprodución</string>
<string name="accessibility_time_position">%1$s de %2$s</string>
<string name="action_change_playback_speed">Cambiar velocidade de reprodución</string>
<string name="action_clear_date_filter">Borrar filtro de data</string>
<string name="action_clear_station_filter">Borrar filtro de emisora</string>
<string name="action_forward_15s">Adiantar 15 segundos</string>
<string name="action_reset_playback_speed">Restablecer velocidade de reprodución a 1x</string>
<string name="action_rewind_15s">Retroceder 15 segundos</string>
<string name="action_scroll_to_top">Desprazar cara arriba</string>
<string name="dialog_replace_recording_message">Estase a gravar \"%1$s\" neste momento. Queres deter e gardar a gravación actual e comezar a gravar \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Gravación en curso</string>
<string name="filter_all_stations">Todas as emisoras</string>
<string name="filter_date_any">Calquera data</string>

[values-gu]
<string name="accessibility_filter_date_from">તારીખથી: %1$s</string>
<string name="accessibility_filter_date_to">તારીખ સુધી: %1$s</string>
<string name="accessibility_filter_station">સ્ટેશન ફિલ્ટર: %1$s</string>
<string name="accessibility_playback_speed">પ્લેબેક ઝડપ %1$s</string>
<string name="accessibility_seek_slider">પ્લેબેક સ્થિતિ</string>
<string name="accessibility_time_position">%2$s માંથી %1$s</string>
<string name="action_change_playback_speed">પ્લેબેક ઝડપ બદલો</string>
<string name="action_clear_date_filter">તારીખ ફિલ્ટર સાફ કરો</string>
<string name="action_clear_station_filter">સ્ટેશન ફિલ્ટર સાફ કરો</string>
<string name="action_forward_15s">15 સેકન્ડ આગળ</string>
<string name="action_reset_playback_speed">પ્લેબેક ઝડપ 1x પર રીસેટ કરો</string>
<string name="action_rewind_15s">15 સેકન્ડ પાછળ</string>
<string name="action_scroll_to_top">સૌથી ઉપર સ્ક્રોલ કરો</string>
<string name="dialog_replace_recording_message">\"%1$s\" હાલમાં રેકોર્ડ થઈ રહ્યું છે. શું તમે વર્તમાન રેકોર્ડિંગ રોકીને સાચવવા અને \"%2$s\" નું રેકોર્ડિંગ શરૂ કરવા માંગો છો?</string>
<string name="dialog_replace_recording_title">રેકોર્ડિંગ ચાલુ છે</string>
<string name="filter_all_stations">બધા સ્ટેશનો</string>
<string name="filter_date_any">કોઈપણ તારીખ</string>

[values-ha]
<string name="accessibility_filter_date_from">Daga kwanan wata: %1$s</string>
<string name="accessibility_filter_date_to">Zuwa kwanan wata: %1$s</string>
<string name="accessibility_filter_station">Tace tasha: %1$s</string>
<string name="accessibility_playback_speed">Saurin kunnawa %1$s</string>
<string name="accessibility_seek_slider">Matsayin kunnawa</string>
<string name="accessibility_time_position">%1$s cikin %2$s</string>
<string name="action_change_playback_speed">Canja saurin kunnawa</string>
<string name="action_clear_date_filter">Share tace kwanan wata</string>
<string name="action_clear_station_filter">Share tace tasha</string>
<string name="action_forward_15s">Gaba da sakan 15</string>
<string name="action_reset_playback_speed">Sake saita saurin kunnawa zuwa 1x</string>
<string name="action_rewind_15s">Baya da sakan 15</string>
<string name="action_scroll_to_top">Gungura zuwa sama</string>
<string name="dialog_replace_recording_message">Ana yin rikodin \"%1$s\" a halin yanzu. Kuna son dakatarwa da ajiye rikodin na yanzu sannan ku fara rikodin \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Ana ci gaba da rikodi</string>
<string name="filter_all_stations">Duk tashoshi</string>
<string name="filter_date_any">Kowane kwanan wata</string>

[values-he]
<string name="accessibility_filter_date_from">מתאריך: %1$s</string>
<string name="accessibility_filter_date_to">עד תאריך: %1$s</string>
<string name="accessibility_filter_station">מסנן תחנות: %1$s</string>
<string name="accessibility_playback_speed">מהירות השמעה %1$s</string>
<string name="accessibility_seek_slider">מיקום השמעה</string>
<string name="accessibility_time_position">%1$s מתוך %2$s</string>
<string name="action_change_playback_speed">שנה מהירות השמעה</string>
<string name="action_clear_date_filter">נקה מסנן תאריכים</string>
<string name="action_clear_station_filter">נקה מסנן תחנות</string>
<string name="action_forward_15s">קדימה 15 שניות</string>
<string name="action_reset_playback_speed">אפס מהירות השמעה ל-1x</string>
<string name="action_rewind_15s">אחורה 15 שניות</string>
<string name="action_scroll_to_top">גלול לראש הרשימה</string>
<string name="dialog_replace_recording_message">\"%1$s\" מוקלט כעת. האם לעצור ולשמור את ההקלטה הנוכחית ולהתחיל להקליט את \"%2$s\"?</string>
<string name="dialog_replace_recording_title">ההקלטה מתבצעת</string>
<string name="filter_all_stations">כל התחנות</string>
<string name="filter_date_any">כל תאריך</string>

[values-hi]
<string name="accessibility_filter_date_from">तारीख से: %1$s</string>
<string name="accessibility_filter_date_to">तारीख तक: %1$s</string>
<string name="accessibility_filter_station">स्टेशन फ़िल्टर: %1$s</string>
<string name="accessibility_playback_speed">प्लेबैक गति %1$s</string>
<string name="accessibility_seek_slider">प्लेबैक स्थिति</string>
<string name="accessibility_time_position">%2$s में से %1$s</string>
<string name="action_change_playback_speed">प्लेबैक गति बदलें</string>
<string name="action_clear_date_filter">तारीख फ़िल्टर साफ़ करें</string>
<string name="action_clear_station_filter">स्टेशन फ़िल्टर साफ़ करें</string>
<string name="action_forward_15s">15 सेकंड आगे बढ़ाएं</string>
<string name="action_reset_playback_speed">प्लेबैक गति 1x पर रीसेट करें</string>
<string name="action_rewind_15s">15 सेकंड पीछे करें</string>
<string name="action_scroll_to_top">सबसे ऊपर स्क्रॉल करें</string>
<string name="dialog_replace_recording_message">\"%1$s\" वर्तमान में रिकॉर्ड हो रहा है। क्या आप वर्तमान रिकॉर्डिंग रोककर सहेजना चाहते हैं और \"%2$s\" की रिकॉर्डिंग शुरू करना चाहते हैं?</string>
<string name="dialog_replace_recording_title">रिकॉर्डिंग जारी है</string>
<string name="filter_all_stations">सभी स्टेशन</string>
<string name="filter_date_any">कोई भी तारीख</string>

[values-hr]
<string name="accessibility_filter_date_from">Od datuma: %1$s</string>
<string name="accessibility_filter_date_to">Do datuma: %1$s</string>
<string name="accessibility_filter_station">Filtar stanice: %1$s</string>
<string name="accessibility_playback_speed">Brzina reprodukcije %1$s</string>
<string name="accessibility_seek_slider">Položaj reprodukcije</string>
<string name="accessibility_time_position">%1$s od %2$s</string>
<string name="action_change_playback_speed">Promijeni brzinu reprodukcije</string>
<string name="action_clear_date_filter">Očisti filtar datuma</string>
<string name="action_clear_station_filter">Očisti filtar stanice</string>
<string name="action_forward_15s">Naprijed 15 sekundi</string>
<string name="action_reset_playback_speed">Vrati brzinu reprodukcije na 1x</string>
<string name="action_rewind_15s">Natrag 15 sekundi</string>
<string name="action_scroll_to_top">Pomakni na vrh</string>
<string name="dialog_replace_recording_message">\"%1$s\" se trenutno snima. Želite li zaustaviti i spremiti trenutno snimanje te započeti snimanje \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Snimanje u tijeku</string>
<string name="filter_all_stations">Sve stanice</string>
<string name="filter_date_any">Bilo koji datum</string>

[values-hu]
<string name="accessibility_filter_date_from">Dátumtól: %1$s</string>
<string name="accessibility_filter_date_to">Dátumig: %1$s</string>
<string name="accessibility_filter_station">Állomásszűrő: %1$s</string>
<string name="accessibility_playback_speed">Lejátszási sebesség %1$s</string>
<string name="accessibility_seek_slider">Lejátszási pozíció</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Lejátszási sebesség módosítása</string>
<string name="action_clear_date_filter">Dátumszűrő törlése</string>
<string name="action_clear_station_filter">Állomásszűrő törlése</string>
<string name="action_forward_15s">Előre 15 másodperccel</string>
<string name="action_reset_playback_speed">Lejátszási sebesség visszaállítása 1x-re</string>
<string name="action_rewind_15s">Vissza 15 másodperccel</string>
<string name="action_scroll_to_top">Görgetés legfelülre</string>
<string name="dialog_replace_recording_message">\"%1$s\" felvétele jelenleg folyamatban van. Leállítja és menti az aktuális felvételt, majd elindítja a(z) \"%2$s\" felvételét?</string>
<string name="dialog_replace_recording_title">Felvétel folyamatban</string>
<string name="filter_all_stations">Összes állomás</string>
<string name="filter_date_any">Bármilyen dátum</string>

[values-hy]
<string name="accessibility_filter_date_from">Սկսած ամսաթվից՝ %1$s</string>
<string name="accessibility_filter_date_to">Մինչև ամսաթիվը՝ %1$s</string>
<string name="accessibility_filter_station">Կայանի զտիչ՝ %1$s</string>
<string name="accessibility_playback_speed">Նվագարկման արագություն %1$s</string>
<string name="accessibility_seek_slider">Նվագարկման դիրք</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Փոխել նվագարկման արագությունը</string>
<string name="action_clear_date_filter">Մաքրել ամսաթվի զտիչը</string>
<string name="action_clear_station_filter">Մաքրել կայանի զտիչը</string>
<string name="action_forward_15s">Առաջ 15 վայրկյան</string>
<string name="action_reset_playback_speed">Վերականգնել նվագարկման արագությունը 1x-ի</string>
<string name="action_rewind_15s">Հետ 15 վայրկյան</string>
<string name="action_scroll_to_top">Ոլորել վերև</string>
<string name="dialog_replace_recording_message">«%1$s»-ը ներկայումս ձայնագրվում է: Ցանկանու՞մ եք դադարեցնել և պահպանել ընթացիկ ձայնագրությունը և սկսել «%2$s»-ի ձայնագրումը:</string>
<string name="dialog_replace_recording_title">Ընթանում է ձայնագրում</string>
<string name="filter_all_stations">Բոլոր կայանները</string>
<string name="filter_date_any">Ցանկացած ամսաթիվ</string>

[values-id]
<string name="accessibility_filter_date_from">Dari tanggal: %1$s</string>
<string name="accessibility_filter_date_to">Sampai tanggal: %1$s</string>
<string name="accessibility_filter_station">Filter stasiun: %1$s</string>
<string name="accessibility_playback_speed">Kecepatan pemutaran %1$s</string>
<string name="accessibility_seek_slider">Posisi pemutaran</string>
<string name="accessibility_time_position">%1$s dari %2$s</string>
<string name="action_change_playback_speed">Ubah kecepatan pemutaran</string>
<string name="action_clear_date_filter">Hapus filter tanggal</string>
<string name="action_clear_station_filter">Hapus filter stasiun</string>
<string name="action_forward_15s">Maju 15 detik</string>
<string name="action_reset_playback_speed">Atur ulang kecepatan pemutaran ke 1x</string>
<string name="action_rewind_15s">Mundur 15 detik</string>
<string name="action_scroll_to_top">Gulir ke paling atas</string>
<string name="dialog_replace_recording_message">\"%1$s\" sedang direkam. Apakah Anda ingin menghentikan dan menyimpan rekaman saat ini lalu mulai merekam \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Perekaman sedang berlangsung</string>
<string name="filter_all_stations">Semua stasiun</string>
<string name="filter_date_any">Tanggal berapa pun</string>

[values-ig]
<string name="accessibility_filter_date_from">Site na ụbọchị: %1$s</string>
<string name="accessibility_filter_date_to">Ruo ụbọchị: %1$s</string>
<string name="accessibility_filter_station">Nzacha ọdụ: %1$s</string>
<string name="accessibility_playback_speed">Ọsọ ọkpụkpọ %1$s</string>
<string name="accessibility_seek_slider">Ọnọdụ ọkpụkpọ</string>
<string name="accessibility_time_position">%1$s n\'ime %2$s</string>
<string name="action_change_playback_speed">Gbanwee ọsọ ọkpụkpọ</string>
<string name="action_clear_date_filter">Hichapụ nzacha ụbọchị</string>
<string name="action_clear_station_filter">Hichapụ nzacha ọdụ</string>
<string name="action_forward_15s">Gaa n\'ihu sekọnd 15</string>
<string name="action_reset_playback_speed">Tọgharịa ọsọ ọkpụkpọ ka ọ bụrụ 1x</string>
<string name="action_rewind_15s">Laghachi azụ sekọnd 15</string>
<string name="action_scroll_to_top">Pịgharịa gaa n\'elu</string>
<string name="dialog_replace_recording_message">A na-edekọ \"%1$s\" ugbu a. Ị chọrọ ịkwụsị ma chekwaa ndekọ dị ugbu a wee malite ịdekọ \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Ndekọ na-aga n\'ihu</string>
<string name="filter_all_stations">Ọdụ niile</string>
<string name="filter_date_any">Ụbọchị ọ bụla</string>

[values-in]
<string name="accessibility_filter_date_from">Dari tanggal: %1$s</string>
<string name="accessibility_filter_date_to">Sampai tanggal: %1$s</string>
<string name="accessibility_filter_station">Filter stasiun: %1$s</string>
<string name="accessibility_playback_speed">Kecepatan pemutaran %1$s</string>
<string name="accessibility_seek_slider">Posisi pemutaran</string>
<string name="accessibility_time_position">%1$s dari %2$s</string>
<string name="action_change_playback_speed">Ubah kecepatan pemutaran</string>
<string name="action_clear_date_filter">Hapus filter tanggal</string>
<string name="action_clear_station_filter">Hapus filter stasiun</string>
<string name="action_forward_15s">Maju 15 detik</string>
<string name="action_reset_playback_speed">Atur ulang kecepatan pemutaran ke 1x</string>
<string name="action_rewind_15s">Mundur 15 detik</string>
<string name="action_scroll_to_top">Gulir ke paling atas</string>
<string name="dialog_replace_recording_message">\"%1$s\" sedang direkam. Apakah Anda ingin menghentikan dan menyimpan rekaman saat ini lalu mulai merekam \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Perekaman sedang berlangsung</string>
<string name="filter_all_stations">Semua stasiun</string>
<string name="filter_date_any">Tanggal berapa pun</string>

[values-is]
<string name="accessibility_filter_date_from">Frá dagsetningu: %1$s</string>
<string name="accessibility_filter_date_to">Til dagsetningar: %1$s</string>
<string name="accessibility_filter_station">Stöðvarsía: %1$s</string>
<string name="accessibility_playback_speed">Afspilunarhraði %1$s</string>
<string name="accessibility_seek_slider">Afspilunarstaða</string>
<string name="accessibility_time_position">%1$s af %2$s</string>
<string name="action_change_playback_speed">Breyta afspilunarhraða</string>
<string name="action_clear_date_filter">Hreinsa dagsetningarsíu</string>
<string name="action_clear_station_filter">Hreinsa stöðvarsíu</string>
<string name="action_forward_15s">Áfram um 15 sekúndur</string>
<string name="action_reset_playback_speed">Endurstilla afspilunarhraða í 1x</string>
<string name="action_rewind_15s">Til baka um 15 sekúndur</string>
<string name="action_scroll_to_top">Skruna efst</string>
<string name="dialog_replace_recording_message">Verið er að taka upp „%1$s“. Viltu stöðva og vista núverandi upptöku og hefja upptöku á „%2$s“?</string>
<string name="dialog_replace_recording_title">Upptaka í gangi</string>
<string name="filter_all_stations">Allar stöðvar</string>
<string name="filter_date_any">Hvaða dagsetning sem er</string>

[values-it]
<string name="accessibility_filter_date_from">Dalla data: %1$s</string>
<string name="accessibility_filter_date_to">Alla data: %1$s</string>
<string name="accessibility_filter_station">Filtro stazione: %1$s</string>
<string name="accessibility_playback_speed">Velocità di riproduzione %1$s</string>
<string name="accessibility_seek_slider">Posizione di riproduzione</string>
<string name="accessibility_time_position">%1$s di %2$s</string>
<string name="action_change_playback_speed">Cambia velocità di riproduzione</string>
<string name="action_clear_date_filter">Cancella filtro data</string>
<string name="action_clear_station_filter">Cancella filtro stazione</string>
<string name="action_forward_15s">Avanti di 15 secondi</string>
<string name="action_reset_playback_speed">Ripristina velocità di riproduzione a 1x</string>
<string name="action_rewind_15s">Indietro di 15 secondi</string>
<string name="action_scroll_to_top">Scorri fino in cima</string>
<string name="dialog_replace_recording_message">Registrazione di \"%1$s\" in corso. Vuoi interrompere e salvare la registrazione attuale e avviare la registrazione di \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Registrazione in corso</string>
<string name="filter_all_stations">Tutte le stazioni</string>
<string name="filter_date_any">Qualsiasi data</string>

[values-iw]
<string name="accessibility_filter_date_from">מתאריך: %1$s</string>
<string name="accessibility_filter_date_to">עד תאריך: %1$s</string>
<string name="accessibility_filter_station">מסנן תחנות: %1$s</string>
<string name="accessibility_playback_speed">מהירות השמעה %1$s</string>
<string name="accessibility_seek_slider">מיקום השמעה</string>
<string name="accessibility_time_position">%1$s מתוך %2$s</string>
<string name="action_change_playback_speed">שנה מהירות השמעה</string>
<string name="action_clear_date_filter">נקה מסנן תאריכים</string>
<string name="action_clear_station_filter">נקה מסנן תחנות</string>
<string name="action_forward_15s">קדימה 15 שניות</string>
<string name="action_reset_playback_speed">אפס מהירות השמעה ל-1x</string>
<string name="action_rewind_15s">אחורה 15 שניות</string>
<string name="action_scroll_to_top">גלול לראש הרשימה</string>
<string name="dialog_replace_recording_message">\"%1$s\" מוקלט כעת. האם לעצור ולשמור את ההקלטה הנוכחית ולהתחיל להקליט את \"%2$s\"?</string>
<string name="dialog_replace_recording_title">ההקלטה מתבצעת</string>
<string name="filter_all_stations">כל התחנות</string>
<string name="filter_date_any">כל תאריך</string>

[values-ja]
<string name="accessibility_filter_date_from">開始日: %1$s</string>
<string name="accessibility_filter_date_to">終了日: %1$s</string>
<string name="accessibility_filter_station">放送局フィルター: %1$s</string>
<string name="accessibility_playback_speed">再生速度 %1$s</string>
<string name="accessibility_seek_slider">再生位置</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">再生速度を変更</string>
<string name="action_clear_date_filter">日付フィルターをクリア</string>
<string name="action_clear_station_filter">放送局フィルターをクリア</string>
<string name="action_forward_15s">15秒早送り</string>
<string name="action_reset_playback_speed">再生速度を 1x にリセット</string>
<string name="action_rewind_15s">15秒巻き戻し</string>
<string name="action_scroll_to_top">一番上へスクロール</string>
<string name="dialog_replace_recording_message">「%1$s」を録音中です。現在の録音を停止して保存し、「%2$s」の録音を開始しますか？</string>
<string name="dialog_replace_recording_title">録音中</string>
<string name="filter_all_stations">すべての放送局</string>
<string name="filter_date_any">すべての日付</string>

[values-ji]
<string name="accessibility_filter_date_from">פֿון דאַטע: %1$s</string>
<string name="accessibility_filter_date_to">ביז דאַטע: %1$s</string>
<string name="accessibility_filter_station">סטאַנציע פֿילטער: %1$s</string>
<string name="accessibility_playback_speed">שפּיל-גיכקייט %1$s</string>
<string name="accessibility_seek_slider">שפּיל-פּאָזיציע</string>
<string name="accessibility_time_position">%1$s פֿון %2$s</string>
<string name="action_change_playback_speed">ענדערן שפּיל-גיכקייט</string>
<string name="action_clear_date_filter">אויסמעקן דאַטע פֿילטער</string>
<string name="action_clear_station_filter">אויסמעקן סטאַנציע פֿילטער</string>
<string name="action_forward_15s">פֿאָרויס 15 סעקונדעס</string>
<string name="action_reset_playback_speed">צוריקשטעלן שפּיל-גיכקייט צו 1x</string>
<string name="action_rewind_15s">צוריק 15 סעקונדעס</string>
<string name="action_scroll_to_top">סקראָלן צום שפּיץ</string>
<string name="dialog_replace_recording_message">\"%1$s\" ווערט איצט רעקאָרדירט. ווילט איר אָפּשטעלן און היטן די איצטיקע רעקאָרדירונג און אָנהייבן רעקאָרדירן \"%2$s\"?</string>
<string name="dialog_replace_recording_title">רעקאָרדירונג אין גאַנג</string>
<string name="filter_all_stations">אַלע סטאַנציעס</string>
<string name="filter_date_any">יעדע דאַטע</string>

[values-ka]
<string name="accessibility_filter_date_from">თარიღიდან: %1$s</string>
<string name="accessibility_filter_date_to">თარიღამდე: %1$s</string>
<string name="accessibility_filter_station">სადგურის ფილტრი: %1$s</string>
<string name="accessibility_playback_speed">დაკვრის სიჩქარე %1$s</string>
<string name="accessibility_seek_slider">დაკვრის პოზიცია</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">დაკვრის სიჩქარის შეცვლა</string>
<string name="action_clear_date_filter">თარიღის ფილტრის გასუფთავება</string>
<string name="action_clear_station_filter">სადგურის ფილტრის გასუფთავება</string>
<string name="action_forward_15s">15 წამით წინ</string>
<string name="action_reset_playback_speed">დაკვრის სიჩქარის 1x-ზე დაბრუნება</string>
<string name="action_rewind_15s">15 წამით უკან</string>
<string name="action_scroll_to_top">თავში ასვლა</string>
<string name="dialog_replace_recording_message">ამჟამად მიმდინარეობს \"%1$s\"-ის ჩაწერა. გსურთ შეაჩეროთ და შეინახოთ მიმდინარე ჩანაწერი და დაიწყოთ \"%2$s\"-ის ჩაწერა?</string>
<string name="dialog_replace_recording_title">მიმდინარეობს ჩაწერა</string>
<string name="filter_all_stations">ყველა სადგური</string>
<string name="filter_date_any">ნებისმიერი თარიღი</string>

[values-kk]
<string name="accessibility_filter_date_from">Күнінен бастап: %1$s</string>
<string name="accessibility_filter_date_to">Күніне дейін: %1$s</string>
<string name="accessibility_filter_station">Станция сүзгісі: %1$s</string>
<string name="accessibility_playback_speed">Ойнату жылдамдығы %1$s</string>
<string name="accessibility_seek_slider">Ойнату орны</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Ойнату жылдамдығын өзгерту</string>
<string name="action_clear_date_filter">Күн сүзгісін тазалау</string>
<string name="action_clear_station_filter">Станция сүзгісін тазалау</string>
<string name="action_forward_15s">15 секунд алға</string>
<string name="action_reset_playback_speed">Ойнату жылдамдығын 1x мәніне қайтару</string>
<string name="action_rewind_15s">15 секунд артқа</string>
<string name="action_scroll_to_top">Ең жоғарыға айналдыру</string>
<string name="dialog_replace_recording_message">Қазір \"%1$s\" жазылып жатыр. Ағымдағы жазбаны тоқтатып, сақтап, \"%2$s\" жазуды бастағыңыз келе ме?</string>
<string name="dialog_replace_recording_title">Жазу жүріп жатыр</string>
<string name="filter_all_stations">Барлық станциялар</string>
<string name="filter_date_any">Кез келген күн</string>

[values-km]
<string name="accessibility_filter_date_from">ចាប់ពីកាលបរិច្ឆេទ៖ %1$s</string>
<string name="accessibility_filter_date_to">ដល់កាលបរិច្ឆេទ៖ %1$s</string>
<string name="accessibility_filter_station">តម្រងស្ថានីយ៖ %1$s</string>
<string name="accessibility_playback_speed">ល្បឿនចាក់ %1$s</string>
<string name="accessibility_seek_slider">ទីតាំងចាក់</string>
<string name="accessibility_time_position">%1$s នៃ %2$s</string>
<string name="action_change_playback_speed">ប្តូរល្បឿនចាក់</string>
<string name="action_clear_date_filter">សម្អាតតម្រងកាលបរិច្ឆេទ</string>
<string name="action_clear_station_filter">សម្អាតតម្រងស្ថានីយ</string>
<string name="action_forward_15s">ទៅមុខ 15 វិនាទី</string>
<string name="action_reset_playback_speed">កំណត់ល្បឿនចាក់ឡើងវិញទៅ 1x</string>
<string name="action_rewind_15s">ថយក្រោយ 15 វិនាទី</string>
<string name="action_scroll_to_top">រំកិលទៅកំពូល</string>
<string name="dialog_replace_recording_message">\"%1$s\" កំពុងត្រូវបានថត។ តើអ្នកចង់បញ្ឈប់ និងរក្សាទុកការថតបច្ចុប្បន្ន ហើយចាប់ផ្តើមថត \"%2$s\" ទេ?</string>
<string name="dialog_replace_recording_title">កំពុងថត</string>
<string name="filter_all_stations">គ្រប់ស្ថានីយ</string>
<string name="filter_date_any">កាលបរិច្ឆេទណាមួយ</string>

[values-kn]
<string name="accessibility_filter_date_from">ದಿನಾಂಕದಿಂದ: %1$s</string>
<string name="accessibility_filter_date_to">ದಿನಾಂಕದವರೆಗೆ: %1$s</string>
<string name="accessibility_filter_station">ನಿಲ್ದಾಣದ ಫಿಲ್ಟರ್: %1$s</string>
<string name="accessibility_playback_speed">ಪ್ಲೇಬ್ಯಾಕ್ ವೇಗ %1$s</string>
<string name="accessibility_seek_slider">ಪ್ಲೇಬ್ಯಾಕ್ ಸ್ಥಾನ</string>
<string name="accessibility_time_position">%2$s ರಲ್ಲಿ %1$s</string>
<string name="action_change_playback_speed">ಪ್ಲೇಬ್ಯಾಕ್ ವೇಗ ಬದಲಾಯಿಸಿ</string>
<string name="action_clear_date_filter">ದಿನಾಂಕ ಫಿಲ್ಟರ್ ತೆರವುಗೊಳಿಸಿ</string>
<string name="action_clear_station_filter">ನಿಲ್ದಾಣದ ಫಿಲ್ಟರ್ ತೆರವುಗೊಳಿಸಿ</string>
<string name="action_forward_15s">15 ಸೆಕೆಂಡು ಮುಂದಕ್ಕೆ</string>
<string name="action_reset_playback_speed">ಪ್ಲೇಬ್ಯಾಕ್ ವೇಗವನ್ನು 1x ಗೆ ಮರುಹೊಂದಿಸಿ</string>
<string name="action_rewind_15s">15 ಸೆಕೆಂಡು ಹಿಂದಕ್ಕೆ</string>
<string name="action_scroll_to_top">ಮೇಲಕ್ಕೆ ಸ್ಕ್ರಾಲ್ ಮಾಡಿ</string>
<string name="dialog_replace_recording_message">\"%1$s\" ಪ್ರಸ್ತುತ ರೆಕಾರ್ಡ್ ಆಗುತ್ತಿದೆ. ಪ್ರಸ್ತುತ ರೆಕಾರ್ಡಿಂಗ್ ನಿಲ್ಲಿಸಿ ಉಳಿಸಿ ಮತ್ತು \"%2$s\" ರೆಕಾರ್ಡಿಂಗ್ ಪ್ರಾರಂಭಿಸಲು ಬಯಸುವಿರಾ?</string>
<string name="dialog_replace_recording_title">ರೆಕಾರ್ಡಿಂಗ್ ನಡೆಯುತ್ತಿದೆ</string>
<string name="filter_all_stations">ಎಲ್ಲಾ ನಿಲ್ದಾಣಗಳು</string>
<string name="filter_date_any">ಯಾವುದೇ ದಿನಾಂಕ</string>

[values-ko]
<string name="accessibility_filter_date_from">시작 날짜: %1$s</string>
<string name="accessibility_filter_date_to">종료 날짜: %1$s</string>
<string name="accessibility_filter_station">방송국 필터: %1$s</string>
<string name="accessibility_playback_speed">재생 속도 %1$s</string>
<string name="accessibility_seek_slider">재생 위치</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">재생 속도 변경</string>
<string name="action_clear_date_filter">날짜 필터 지우기</string>
<string name="action_clear_station_filter">방송국 필터 지우기</string>
<string name="action_forward_15s">15초 앞으로</string>
<string name="action_reset_playback_speed">재생 속도를 1x로 초기화</string>
<string name="action_rewind_15s">15초 뒤로</string>
<string name="action_scroll_to_top">맨 위로 스크롤</string>
<string name="dialog_replace_recording_message">\"%1$s\" 녹음이 진행 중입니다. 현재 녹음을 중지 및 저장하고 \"%2$s\" 녹음을 시작하시겠습니까?</string>
<string name="dialog_replace_recording_title">녹음 진행 중</string>
<string name="filter_all_stations">모든 방송국</string>
<string name="filter_date_any">모든 날짜</string>

[values-ky]
<string name="accessibility_filter_date_from">Датадан баштап: %1$s</string>
<string name="accessibility_filter_date_to">Датага чейин: %1$s</string>
<string name="accessibility_filter_station">Станция чыпкасы: %1$s</string>
<string name="accessibility_playback_speed">Ойнотуу ылдамдыгы %1$s</string>
<string name="accessibility_seek_slider">Ойнотуу абалы</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Ойнотуу ылдамдыгын өзгөртүү</string>
<string name="action_clear_date_filter">Дата чыпкасын тазалоо</string>
<string name="action_clear_station_filter">Станция чыпкасын тазалоо</string>
<string name="action_forward_15s">15 секунд алдыга</string>
<string name="action_reset_playback_speed">Ойнотуу ылдамдыгын 1x абалына келтирүү</string>
<string name="action_rewind_15s">15 секунд артка</string>
<string name="action_scroll_to_top">Эң жогоруга сыдыруу</string>
<string name="dialog_replace_recording_message">Учурда \"%1$s\" жазылып жатат. Учурдагы жаздырууну токтотуп, сактап, \"%2$s\" жаздырууну баштагыңыз келеби?</string>
<string name="dialog_replace_recording_title">Жаздыруу жүрүп жатат</string>
<string name="filter_all_stations">Бардык станциялар</string>
<string name="filter_date_any">Каалаган дата</string>

[values-lo]
<string name="accessibility_filter_date_from">ຈາກວັນທີ: %1$s</string>
<string name="accessibility_filter_date_to">ເຖິງວັນທີ: %1$s</string>
<string name="accessibility_filter_station">ຕົວກອງສະຖານີ: %1$s</string>
<string name="accessibility_playback_speed">ຄວາມໄວການຫຼິ້ນ %1$s</string>
<string name="accessibility_seek_slider">ຕຳແໜ່ງການຫຼິ້ນ</string>
<string name="accessibility_time_position">%1$s ຈາກ %2$s</string>
<string name="action_change_playback_speed">ປ່ຽນຄວາມໄວການຫຼິ້ນ</string>
<string name="action_clear_date_filter">ລ້າງຕົວກອງວັນທີ</string>
<string name="action_clear_station_filter">ລ້າງຕົວກອງສະຖານີ</string>
<string name="action_forward_15s">ໄປໜ້າ 15 ວິນາທີ</string>
<string name="action_reset_playback_speed">ຣີເຊັດຄວາມໄວການຫຼິ້ນເປັນ 1x</string>
<string name="action_rewind_15s">ກັບຫຼັງ 15 ວິນາທີ</string>
<string name="action_scroll_to_top">ເລື່ອນໄປເທິງສຸດ</string>
<string name="dialog_replace_recording_message">ກຳລັງບັນທຶກ \"%1$s\" ຢູ່. ທ່ານຕ້ອງການຢຸດ ແລະ ບັນທຶກການອັດສຽງປັດຈຸບັນ ແລ້ວເລີ່ມບັນທຶກ \"%2$s\" ບໍ?</string>
<string name="dialog_replace_recording_title">ກຳລັງບັນທຶກສຽງ</string>
<string name="filter_all_stations">ທຸກສະຖານີ</string>
<string name="filter_date_any">ວັນທີໃດກໍໄດ້</string>

[values-lt]
<string name="accessibility_filter_date_from">Nuo datos: %1$s</string>
<string name="accessibility_filter_date_to">Iki datos: %1$s</string>
<string name="accessibility_filter_station">Stoties filtras: %1$s</string>
<string name="accessibility_playback_speed">Atkūrimo greitis %1$s</string>
<string name="accessibility_seek_slider">Atkūrimo vieta</string>
<string name="accessibility_time_position">%1$s iš %2$s</string>
<string name="action_change_playback_speed">Keisti atkūrimo greitį</string>
<string name="action_clear_date_filter">Išvalyti datos filtrą</string>
<string name="action_clear_station_filter">Išvalyti stoties filtrą</string>
<string name="action_forward_15s">Pirmyn 15 sekundžių</string>
<string name="action_reset_playback_speed">Atkurti 1x atkūrimo greitį</string>
<string name="action_rewind_15s">Atgal 15 sekundžių</string>
<string name="action_scroll_to_top">Slinkti į viršų</string>
<string name="dialog_replace_recording_message">Šiuo metu įrašoma \"%1$s\". Ar norite sustabdyti ir išsaugoti dabartinį įrašą bei pradėti įrašyti \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Vyksta įrašymas</string>
<string name="filter_all_stations">Visos stotys</string>
<string name="filter_date_any">Bet kokia data</string>

[values-lv]
<string name="accessibility_filter_date_from">No datuma: %1$s</string>
<string name="accessibility_filter_date_to">Līdz datumam: %1$s</string>
<string name="accessibility_filter_station">Stacijas filtrs: %1$s</string>
<string name="accessibility_playback_speed">Atskaņošanas ātrums %1$s</string>
<string name="accessibility_seek_slider">Atskaņošanas pozīcija</string>
<string name="accessibility_time_position">%1$s no %2$s</string>
<string name="action_change_playback_speed">Mainīt atskaņošanas ātrumu</string>
<string name="action_clear_date_filter">Notīrīt datuma filtru</string>
<string name="action_clear_station_filter">Notīrīt stacijas filtru</string>
<string name="action_forward_15s">Uz priekšu 15 sekundes</string>
<string name="action_reset_playback_speed">Atiestatīt atskaņošanas ātrumu uz 1x</string>
<string name="action_rewind_15s">Atpakaļ 15 sekundes</string>
<string name="action_scroll_to_top">Ritināt uz augšu</string>
<string name="dialog_replace_recording_message">Pašlaik tiek ierakstīts \"%1$s\". Vai vēlaties apturēt un saglabāt pašreizējo ierakstu un sākt ierakstīt \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Notiek ierakstīšana</string>
<string name="filter_all_stations">Visas stacijas</string>
<string name="filter_date_any">Jebkurš datums</string>

[values-mk]
<string name="accessibility_filter_date_from">Од датум: %1$s</string>
<string name="accessibility_filter_date_to">До датум: %1$s</string>
<string name="accessibility_filter_station">Филтер за станица: %1$s</string>
<string name="accessibility_playback_speed">Брзина на репродукција %1$s</string>
<string name="accessibility_seek_slider">Позиција на репродукција</string>
<string name="accessibility_time_position">%1$s од %2$s</string>
<string name="action_change_playback_speed">Промени брзина на репродукција</string>
<string name="action_clear_date_filter">Исчисти филтер за датум</string>
<string name="action_clear_station_filter">Исчисти филтер за станица</string>
<string name="action_forward_15s">Напред 15 секунди</string>
<string name="action_reset_playback_speed">Врати брзина на репродукција на 1x</string>
<string name="action_rewind_15s">Назад 15 секунди</string>
<string name="action_scroll_to_top">Лизгај на врв</string>
<string name="dialog_replace_recording_message">Во моментов се снима \"%1$s\". Дали сакате да го запрете и зачувате тековното снимање и да започнете со снимање на \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Снимањето е во тек</string>
<string name="filter_all_stations">Сите станици</string>
<string name="filter_date_any">Било кој датум</string>

[values-ml]
<string name="accessibility_filter_date_from">തീയതി മുതൽ: %1$s</string>
<string name="accessibility_filter_date_to">തീയതി വരെ: %1$s</string>
<string name="accessibility_filter_station">സ്റ്റേഷൻ ഫിൽട്ടർ: %1$s</string>
<string name="accessibility_playback_speed">പ്ലേബാക്ക് വേഗത %1$s</string>
<string name="accessibility_seek_slider">പ്ലേബാക്ക് സ്ഥാനം</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">പ്ലേബാക്ക് വേഗത മാറ്റുക</string>
<string name="action_clear_date_filter">തീയതി ഫിൽട്ടർ മായ്ക്കുക</string>
<string name="action_clear_station_filter">സ്റ്റേഷൻ ഫിൽട്ടർ മായ്ക്കുക</string>
<string name="action_forward_15s">15 സെക്കൻഡ് മുന്നോട്ട്</string>
<string name="action_reset_playback_speed">പ്ലേബാക്ക് വേഗത 1x ആക്കുക</string>
<string name="action_rewind_15s">15 സെക്കൻഡ് പിന്നോട്ട്</string>
<string name="action_scroll_to_top">ഏറ്റവും മുകളിലേക്ക് സ്ക്രോൾ ചെയ്യുക</string>
<string name="dialog_replace_recording_message">\"%1$s\" നിലവിൽ റെക്കോർഡ് ചെയ്യുകയാണ്. നിലവിലെ റെക്കോർഡിംഗ് നിർത്തി സംരക്ഷിച്ച ശേഷം \"%2$s\" റെക്കോർഡിംഗ് ആരംഭിക്കണോ?</string>
<string name="dialog_replace_recording_title">റെക്കോർഡിംഗ് നടക്കുന്നു</string>
<string name="filter_all_stations">എല്ലാ സ്റ്റേഷനുകളും</string>
<string name="filter_date_any">ഏത് തീയതിയും</string>

[values-mn]
<string name="accessibility_filter_date_from">Эхлэх огноо: %1$s</string>
<string name="accessibility_filter_date_to">Дуусах огноо: %1$s</string>
<string name="accessibility_filter_station">Станцын шүүлтүүр: %1$s</string>
<string name="accessibility_playback_speed">Тоглуулах хурд %1$s</string>
<string name="accessibility_seek_slider">Тоглуулах байрлал</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Тоглуулах хурд өөрчлөх</string>
<string name="action_clear_date_filter">Огнооны шүүлтүүр арилгах</string>
<string name="action_clear_station_filter">Станцын шүүлтүүр арилгах</string>
<string name="action_forward_15s">15 секунд урагшлуулах</string>
<string name="action_reset_playback_speed">Тоглуулах хурдыг 1x болгох</string>
<string name="action_rewind_15s">15 секунд ухраах</string>
<string name="action_scroll_to_top">Хамгийн дээш гүйлгэх</string>
<string name="dialog_replace_recording_message">\"%1$s\" одоо бичигдэж байна. Одоогийн бичлэгийг зогсоож хадгалаад \"%2$s\"-г бичиж эхлэх үү?</string>
<string name="dialog_replace_recording_title">Бичлэг хийгдэж байна</string>
<string name="filter_all_stations">Бүх станц</string>
<string name="filter_date_any">Дурын огноо</string>

[values-mr]
<string name="accessibility_filter_date_from">तारखेपासून: %1$s</string>
<string name="accessibility_filter_date_to">तारखेपर्यंत: %1$s</string>
<string name="accessibility_filter_station">स्टेशन फिल्टर: %1$s</string>
<string name="accessibility_playback_speed">प्लेबॅक वेग %1$s</string>
<string name="accessibility_seek_slider">प्लेबॅक स्थिती</string>
<string name="accessibility_time_position">%2$s पैकी %1$s</string>
<string name="action_change_playback_speed">प्लेबॅक वेग बदला</string>
<string name="action_clear_date_filter">तारीख फिल्टर साफ करा</string>
<string name="action_clear_station_filter">स्टेशन फिल्टर साफ करा</string>
<string name="action_forward_15s">15 सेकंद पुढे</string>
<string name="action_reset_playback_speed">प्लेबॅक वेग 1x वर रीसेट करा</string>
<string name="action_rewind_15s">15 सेकंद मागे</string>
<string name="action_scroll_to_top">सर्वात वर स्क्रोल करा</string>
<string name="dialog_replace_recording_message">\"%1$s\" सध्या रेकॉर्ड होत आहे. तुम्हाला सध्याचे रेकॉर्डिंग थांबवून सेव्ह करायचे आहे आणि \"%2$s\" रेकॉर्ड करणे सुरू करायचे आहे का?</string>
<string name="dialog_replace_recording_title">रेकॉर्डिंग सुरू आहे</string>
<string name="filter_all_stations">सर्व स्टेशन्स</string>
<string name="filter_date_any">कोणतीही तारीख</string>

[values-ms]
<string name="accessibility_filter_date_from">Dari tarikh: %1$s</string>
<string name="accessibility_filter_date_to">Hingga tarikh: %1$s</string>
<string name="accessibility_filter_station">Penapis stesen: %1$s</string>
<string name="accessibility_playback_speed">Kelajuan main balik %1$s</string>
<string name="accessibility_seek_slider">Kedudukan main balik</string>
<string name="accessibility_time_position">%1$s daripada %2$s</string>
<string name="action_change_playback_speed">Tukar kelajuan main balik</string>
<string name="action_clear_date_filter">Kosongkan penapis tarikh</string>
<string name="action_clear_station_filter">Kosongkan penapis stesen</string>
<string name="action_forward_15s">Maju 15 saat</string>
<string name="action_reset_playback_speed">Tetapkan semula kelajuan main balik ke 1x</string>
<string name="action_rewind_15s">Undur 15 saat</string>
<string name="action_scroll_to_top">Tatal ke paling atas</string>
<string name="dialog_replace_recording_message">\"%1$s\" sedang dirakam. Adakah anda mahu menghentikan dan menyimpan rakaman semasa serta mula merakam \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Rakaman sedang berlangsung</string>
<string name="filter_all_stations">Semua stesen</string>
<string name="filter_date_any">Sebarang tarikh</string>

[values-mt]
<string name="accessibility_filter_date_from">Mid-data: %1$s</string>
<string name="accessibility_filter_date_to">Sad-data: %1$s</string>
<string name="accessibility_filter_station">Filtru tal-istazzjon: %1$s</string>
<string name="accessibility_playback_speed">Veloċità tal-plejbek %1$s</string>
<string name="accessibility_seek_slider">Pożizzjoni tal-plejbek</string>
<string name="accessibility_time_position">%1$s minn %2$s</string>
<string name="action_change_playback_speed">Ibdel il-veloċità tal-plejbek</string>
<string name="action_clear_date_filter">Neħħi l-filtru tad-data</string>
<string name="action_clear_station_filter">Neħħi l-filtru tal-istazzjon</string>
<string name="action_forward_15s">Quddiem 15-il sekonda</string>
<string name="action_reset_playback_speed">Irrisettja l-veloċità tal-plejbek għal 1x</string>
<string name="action_rewind_15s">Lura 15-il sekonda</string>
<string name="action_scroll_to_top">Skrollja fil-quċċata</string>
<string name="dialog_replace_recording_message">\"%1$s\" qed jiġi rreġistrat bħalissa. Trid twaqqaf u ssalva r-reġistrazzjoni attwali u tibda tirreġistra \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Reġistrazzjoni għaddejja</string>
<string name="filter_all_stations">L-istazzjonijiet kollha</string>
<string name="filter_date_any">Kwalunkwe data</string>

[values-my]
<string name="accessibility_filter_date_from">စတင်ရက်စွဲ- %1$s</string>
<string name="accessibility_filter_date_to">ပြီးဆုံးရက်စွဲ- %1$s</string>
<string name="accessibility_filter_station">အသံလွှင့်ဌာန စစ်ထုတ်မှု- %1$s</string>
<string name="accessibility_playback_speed">ဖွင့်သည့်နှုန်း %1$s</string>
<string name="accessibility_seek_slider">ဖွင့်နေသည့်နေရာ</string>
<string name="accessibility_time_position">%2$s အနက် %1$s</string>
<string name="action_change_playback_speed">ဖွင့်သည့်နှုန်း ပြောင်းရန်</string>
<string name="action_clear_date_filter">ရက်စွဲစစ်ထုတ်မှု ရှင်းလင်းရန်</string>
<string name="action_clear_station_filter">အသံလွှင့်ဌာန စစ်ထုတ်မှု ရှင်းလင်းရန်</string>
<string name="action_forward_15s">၁၅ စက္ကန့် ရှေ့တိုးရန်</string>
<string name="action_reset_playback_speed">ဖွင့်သည့်နှုန်းကို 1x သို့ ပြန်ထားရန်</string>
<string name="action_rewind_15s">၁၅ စက္ကန့် နောက်ပြန်ရစ်ရန်</string>
<string name="action_scroll_to_top">ထိပ်ဆုံးသို့ ရွှေ့ရန်</string>
<string name="dialog_replace_recording_message">\"%1$s\" ကို လက်ရှိ အသံဖမ်းနေပါသည်။ လက်ရှိအသံဖမ်းမှုကို ရပ်ပြီး သိမ်းဆည်းကာ \"%2$s\" ကို စတင်အသံဖမ်းလိုပါသလား။</string>
<string name="dialog_replace_recording_title">အသံဖမ်းနေပါသည်</string>
<string name="filter_all_stations">အသံလွှင့်ဌာနအားလုံး</string>
<string name="filter_date_any">မည်သည့်ရက်စွဲမဆို</string>

[values-nb]
<string name="accessibility_filter_date_from">Fra dato: %1$s</string>
<string name="accessibility_filter_date_to">Til dato: %1$s</string>
<string name="accessibility_filter_station">Stasjonsfilter: %1$s</string>
<string name="accessibility_playback_speed">Avspillingshastighet %1$s</string>
<string name="accessibility_seek_slider">Avspillingsposisjon</string>
<string name="accessibility_time_position">%1$s av %2$s</string>
<string name="action_change_playback_speed">Endre avspillingshastighet</string>
<string name="action_clear_date_filter">Tøm datofilter</string>
<string name="action_clear_station_filter">Tøm stasjonsfilter</string>
<string name="action_forward_15s">15 sekunder fremover</string>
<string name="action_reset_playback_speed">Tilbakestill avspillingshastighet til 1x</string>
<string name="action_rewind_15s">15 sekunder bakover</string>
<string name="action_scroll_to_top">Rull til toppen</string>
<string name="dialog_replace_recording_message">\"%1$s\" tas opp akkurat nå. Vil du stoppe og lagre det pågående opptaket og starte opptak av \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Opptak pågår</string>
<string name="filter_all_stations">Alle stasjoner</string>
<string name="filter_date_any">Enhver dato</string>

[values-ne]
<string name="accessibility_filter_date_from">मितिदेखि: %1$s</string>
<string name="accessibility_filter_date_to">मितिसम्म: %1$s</string>
<string name="accessibility_filter_station">स्टेसन फिल्टर: %1$s</string>
<string name="accessibility_playback_speed">प्लेब्याक गति %1$s</string>
<string name="accessibility_seek_slider">प्लेब्याक स्थिति</string>
<string name="accessibility_time_position">%2$s को %1$s</string>
<string name="action_change_playback_speed">प्लेब्याक गति परिवर्तन गर्नुहोस्</string>
<string name="action_clear_date_filter">मिति फिल्टर हटाउनुहोस्</string>
<string name="action_clear_station_filter">स्टेसन फिल्टर हटाउनुहोस्</string>
<string name="action_forward_15s">१५ सेकेन्ड अगाडि</string>
<string name="action_reset_playback_speed">प्लेब्याक गति 1x मा रिसेट गर्नुहोस्</string>
<string name="action_rewind_15s">१५ सेकेन्ड पछाडि</string>
<string name="action_scroll_to_top">सबैभन्दा माथि स्क्रोल गर्नुहोस्</string>
<string name="dialog_replace_recording_message">\"%1$s\" हाल रेकर्ड भइरहेको छ। के तपाईं हालको रेकर्डिङ रोकेर सुरक्षित गर्न र \"%2$s\" रेकर्ड गर्न सुरु गर्न चाहनुहुन्छ?</string>
<string name="dialog_replace_recording_title">रेकर्डिङ भइरहेको छ</string>
<string name="filter_all_stations">सबै स्टेसनहरू</string>
<string name="filter_date_any">कुनै पनि मिति</string>

[values-nl]
<string name="accessibility_filter_date_from">Vanaf datum: %1$s</string>
<string name="accessibility_filter_date_to">Tot datum: %1$s</string>
<string name="accessibility_filter_station">Zenderfilter: %1$s</string>
<string name="accessibility_playback_speed">Afspeelsnelheid %1$s</string>
<string name="accessibility_seek_slider">Afspeelpositie</string>
<string name="accessibility_time_position">%1$s van %2$s</string>
<string name="action_change_playback_speed">Afspeelsnelheid wijzigen</string>
<string name="action_clear_date_filter">Datumfilter wissen</string>
<string name="action_clear_station_filter">Zenderfilter wissen</string>
<string name="action_forward_15s">15 seconden vooruit</string>
<string name="action_reset_playback_speed">Afspeelsnelheid terugzetten naar 1x</string>
<string name="action_rewind_15s">15 seconden terug</string>
<string name="action_scroll_to_top">Naar boven scrollen</string>
<string name="dialog_replace_recording_message">\"%1$s\" wordt momenteel opgenomen. Wilt u de huidige opname stoppen en opslaan en de opname van \"%2$s\" starten?</string>
<string name="dialog_replace_recording_title">Opname bezig</string>
<string name="filter_all_stations">Alle zenders</string>
<string name="filter_date_any">Elke datum</string>

[values-nn]
<string name="accessibility_filter_date_from">Frå dato: %1$s</string>
<string name="accessibility_filter_date_to">Til dato: %1$s</string>
<string name="accessibility_filter_station">Stasjonsfilter: %1$s</string>
<string name="accessibility_playback_speed">Avspelingsfart %1$s</string>
<string name="accessibility_seek_slider">Avspelingsposisjon</string>
<string name="accessibility_time_position">%1$s av %2$s</string>
<string name="action_change_playback_speed">Endre avspelingsfart</string>
<string name="action_clear_date_filter">Tøm datofilter</string>
<string name="action_clear_station_filter">Tøm stasjonsfilter</string>
<string name="action_forward_15s">15 sekund framover</string>
<string name="action_reset_playback_speed">Tilbakestill avspelingsfart til 1x</string>
<string name="action_rewind_15s">15 sekund bakover</string>
<string name="action_scroll_to_top">Rull til toppen</string>
<string name="dialog_replace_recording_message">\"%1$s\" vert teke opp no. Vil du stoppe og lagre det pågåande opptaket og starte opptak av \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Opptak pågår</string>
<string name="filter_all_stations">Alle stasjonar</string>
<string name="filter_date_any">Kva dato som helst</string>

[values-or]
<string name="accessibility_filter_date_from">ତାରିଖଠାରୁ: %1$s</string>
<string name="accessibility_filter_date_to">ତାରିଖ ପର୍ଯ୍ୟନ୍ତ: %1$s</string>
<string name="accessibility_filter_station">ଷ୍ଟେସନ୍ ଫିଲ୍ଟର୍: %1$s</string>
<string name="accessibility_playback_speed">ପ୍ଲେବ୍ୟାକ୍ ଗତି %1$s</string>
<string name="accessibility_seek_slider">ପ୍ଲେବ୍ୟାକ୍ ସ୍ଥିତି</string>
<string name="accessibility_time_position">%2$s ର %1$s</string>
<string name="action_change_playback_speed">ପ୍ଲେବ୍ୟାକ୍ ଗତି ପରିବର୍ତ୍ତନ କରନ୍ତୁ</string>
<string name="action_clear_date_filter">ତାରିଖ ଫିଲ୍ଟର୍ ସଫା କରନ୍ତୁ</string>
<string name="action_clear_station_filter">ଷ୍ଟେସନ୍ ଫିଲ୍ଟର୍ ସଫା କରନ୍ତୁ</string>
<string name="action_forward_15s">୧୫ ସେକେଣ୍ଡ ଆଗକୁ</string>
<string name="action_reset_playback_speed">ପ୍ଲେବ୍ୟାକ୍ ଗତି 1x କୁ ରିସେଟ୍ କରନ୍ତୁ</string>
<string name="action_rewind_15s">୧୫ ସେକେଣ୍ଡ ପଛକୁ</string>
<string name="action_scroll_to_top">ସବୁଠାରୁ ଉପରକୁ ସ୍କ୍ରୋଲ୍ କରନ୍ତୁ</string>
<string name="dialog_replace_recording_message">\"%1$s\" ବର୍ତ୍ତମାନ ରେକର୍ଡ ହେଉଛି। ଆପଣ ବର୍ତ୍ତମାନର ରେକର୍ଡିଂ ବନ୍ଦ କରି ସେଭ୍ କରିବାକୁ ଏବଂ \"%2$s\" ରେକର୍ଡିଂ ଆରମ୍ଭ କରିବାକୁ ଚାହୁଁଛନ୍ତି କି?</string>
<string name="dialog_replace_recording_title">ରେକର୍ଡିଂ ଚାଲିଛି</string>
<string name="filter_all_stations">ସମସ୍ତ ଷ୍ଟେସନ୍</string>
<string name="filter_date_any">ଯେକୌଣସି ତାରିଖ</string>

[values-pa]
<string name="accessibility_filter_date_from">ਮਿਤੀ ਤੋਂ: %1$s</string>
<string name="accessibility_filter_date_to">ਮਿਤੀ ਤੱਕ: %1$s</string>
<string name="accessibility_filter_station">ਸਟੇਸ਼ਨ ਫਿਲਟਰ: %1$s</string>
<string name="accessibility_playback_speed">ਪਲੇਬੈਕ ਗਤੀ %1$s</string>
<string name="accessibility_seek_slider">ਪਲੇਬੈਕ ਸਥਿਤੀ</string>
<string name="accessibility_time_position">%2$s ਵਿੱਚੋਂ %1$s</string>
<string name="action_change_playback_speed">ਪਲੇਬੈਕ ਗਤੀ ਬਦਲੋ</string>
<string name="action_clear_date_filter">ਮਿਤੀ ਫਿਲਟਰ ਸਾਫ਼ ਕਰੋ</string>
<string name="action_clear_station_filter">ਸਟੇਸ਼ਨ ਫਿਲਟਰ ਸਾਫ਼ ਕਰੋ</string>
<string name="action_forward_15s">15 ਸਕਿੰਟ ਅੱਗੇ</string>
<string name="action_reset_playback_speed">ਪਲੇਬੈਕ ਗਤੀ 1x \'ਤੇ ਰੀਸੈਟ ਕਰੋ</string>
<string name="action_rewind_15s">15 ਸਕਿੰਟ ਪਿੱਛੇ</string>
<string name="action_scroll_to_top">ਸਭ ਤੋਂ ਉੱਪਰ ਸਕ੍ਰੋਲ ਕਰੋ</string>
<string name="dialog_replace_recording_message">\"%1$s\" ਇਸ ਵੇਲੇ ਰਿਕਾਰਡ ਹੋ ਰਿਹਾ ਹੈ। ਕੀ ਤੁਸੀਂ ਮੌਜੂਦਾ ਰਿਕਾਰਡਿੰਗ ਰੋਕ ਕੇ ਸੁਰੱਖਿਅਤ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ ਅਤੇ \"%2$s\" ਦੀ ਰਿਕਾਰਡਿੰਗ ਸ਼ੁਰੂ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?</string>
<string name="dialog_replace_recording_title">ਰਿਕਾਰਡਿੰਗ ਜਾਰੀ ਹੈ</string>
<string name="filter_all_stations">ਸਾਰੇ ਸਟੇਸ਼ਨ</string>
<string name="filter_date_any">ਕੋਈ ਵੀ ਮਿਤੀ</string>

[values-pl]
<string name="accessibility_filter_date_from">Od daty: %1$s</string>
<string name="accessibility_filter_date_to">Do daty: %1$s</string>
<string name="accessibility_filter_station">Filtr stacji: %1$s</string>
<string name="accessibility_playback_speed">Prędkość odtwarzania %1$s</string>
<string name="accessibility_seek_slider">Pozycja odtwarzania</string>
<string name="accessibility_time_position">%1$s z %2$s</string>
<string name="action_change_playback_speed">Zmień prędkość odtwarzania</string>
<string name="action_clear_date_filter">Wyczyść filtr daty</string>
<string name="action_clear_station_filter">Wyczyść filtr stacji</string>
<string name="action_forward_15s">Do przodu o 15 sekund</string>
<string name="action_reset_playback_speed">Przywróć prędkość odtwarzania 1x</string>
<string name="action_rewind_15s">Do tyłu o 15 sekund</string>
<string name="action_scroll_to_top">Przewiń na górę</string>
<string name="dialog_replace_recording_message">Trwa nagrywanie stacji „%1$s”. Czy chcesz zatrzymać i zapisać bieżące nagranie oraz rozpocząć nagrywanie „%2$s”?</string>
<string name="dialog_replace_recording_title">Trwa nagrywanie</string>
<string name="filter_all_stations">Wszystkie stacje</string>
<string name="filter_date_any">Dowolna data</string>

[values-ps]
<string name="accessibility_filter_date_from">له نېټې څخه: %1$s</string>
<string name="accessibility_filter_date_to">تر نېټې پورې: %1$s</string>
<string name="accessibility_filter_station">د سټېشن فلټر: %1$s</string>
<string name="accessibility_playback_speed">د غږولو چټکتیا %1$s</string>
<string name="accessibility_seek_slider">د غږولو ځای</string>
<string name="accessibility_time_position">%1$s له %2$s څخه</string>
<string name="action_change_playback_speed">د غږولو چټکتیا بدلول</string>
<string name="action_clear_date_filter">د نېټې فلټر پاکول</string>
<string name="action_clear_station_filter">د سټېشن فلټر پاکول</string>
<string name="action_forward_15s">۱۵ ثانیې مخکې</string>
<string name="action_reset_playback_speed">د غږولو چټکتیا 1x ته بېرته تنظیمول</string>
<string name="action_rewind_15s">۱۵ ثانیې شاته</string>
<string name="action_scroll_to_top">تر ټولو پورته سکرول کول</string>
<string name="dialog_replace_recording_message">\"%1$s\" دا مهال ثبتېږي. ایا غواړئ اوسنی ثبت ودرول او خوندي شي او د \"%2$s\" ثبتول پیل شي؟</string>
<string name="dialog_replace_recording_title">ثبتول روان دي</string>
<string name="filter_all_stations">ټول سټېشنونه</string>
<string name="filter_date_any">هره نېټه</string>

[values-pt]
<string name="accessibility_filter_date_from">A partir da data: %1$s</string>
<string name="accessibility_filter_date_to">Até à data: %1$s</string>
<string name="accessibility_filter_station">Filtro de estação: %1$s</string>
<string name="accessibility_playback_speed">Velocidade de reprodução %1$s</string>
<string name="accessibility_seek_slider">Posição de reprodução</string>
<string name="accessibility_time_position">%1$s de %2$s</string>
<string name="action_change_playback_speed">Alterar velocidade de reprodução</string>
<string name="action_clear_date_filter">Limpar filtro de data</string>
<string name="action_clear_station_filter">Limpar filtro de estação</string>
<string name="action_forward_15s">Avançar 15 segundos</string>
<string name="action_reset_playback_speed">Repor velocidade de reprodução para 1x</string>
<string name="action_rewind_15s">Recuar 15 segundos</string>
<string name="action_scroll_to_top">Deslocar para o topo</string>
<string name="dialog_replace_recording_message">\"%1$s\" está a ser gravada neste momento. Pretende parar e guardar a gravação atual e iniciar a gravação de \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Gravação em curso</string>
<string name="filter_all_stations">Todas as estações</string>
<string name="filter_date_any">Qualquer data</string>

[values-pt-rBR]
<string name="accessibility_filter_date_from">A partir da data: %1$s</string>
<string name="accessibility_filter_date_to">Até a data: %1$s</string>
<string name="accessibility_filter_station">Filtro de estação: %1$s</string>
<string name="accessibility_playback_speed">Velocidade de reprodução %1$s</string>
<string name="accessibility_seek_slider">Posição de reprodução</string>
<string name="accessibility_time_position">%1$s de %2$s</string>
<string name="action_change_playback_speed">Alterar velocidade de reprodução</string>
<string name="action_clear_date_filter">Limpar filtro de data</string>
<string name="action_clear_station_filter">Limpar filtro de estação</string>
<string name="action_forward_15s">Avançar 15 segundos</string>
<string name="action_reset_playback_speed">Redefinir velocidade de reprodução para 1x</string>
<string name="action_rewind_15s">Retroceder 15 segundos</string>
<string name="action_scroll_to_top">Rolar para o topo</string>
<string name="dialog_replace_recording_message">\"%1$s\" está sendo gravada no momento. Deseja parar e salvar a gravação atual e iniciar a gravação de \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Gravação em andamento</string>
<string name="filter_all_stations">Todas as estações</string>
<string name="filter_date_any">Qualquer data</string>

[values-rm]
<string name="accessibility_filter_date_from">Da la data: %1$s</string>
<string name="accessibility_filter_date_to">Enfin a la data: %1$s</string>
<string name="accessibility_filter_station">Filter dad emettur: %1$s</string>
<string name="accessibility_playback_speed">Sveltezza da reproducziun %1$s</string>
<string name="accessibility_seek_slider">Posiziun da reproducziun</string>
<string name="accessibility_time_position">%1$s da %2$s</string>
<string name="action_change_playback_speed">Midar la sveltezza da reproducziun</string>
<string name="action_clear_date_filter">Stizzar il filter da data</string>
<string name="action_clear_station_filter">Stizzar il filter dad emettur</string>
<string name="action_forward_15s">15 secundas enavant</string>
<string name="action_reset_playback_speed">Redefinir la sveltezza da reproducziun sin 1x</string>
<string name="action_rewind_15s">15 secundas enavos</string>
<string name="action_scroll_to_top">Scrollar tut ensi</string>
<string name="dialog_replace_recording_message">«%1$s» vegn gist registrà. Vulais Vus fermar e memorisar la registraziun actuala e cumenzar a registrar «%2$s»?</string>
<string name="dialog_replace_recording_title">Registraziun activa</string>
<string name="filter_all_stations">Tut ils emetturs</string>
<string name="filter_date_any">Mintga data</string>

[values-ro]
<string name="accessibility_filter_date_from">De la data: %1$s</string>
<string name="accessibility_filter_date_to">Până la data: %1$s</string>
<string name="accessibility_filter_station">Filtru post: %1$s</string>
<string name="accessibility_playback_speed">Viteză de redare %1$s</string>
<string name="accessibility_seek_slider">Poziție de redare</string>
<string name="accessibility_time_position">%1$s din %2$s</string>
<string name="action_change_playback_speed">Schimbă viteza de redare</string>
<string name="action_clear_date_filter">Șterge filtrul de dată</string>
<string name="action_clear_station_filter">Șterge filtrul de post</string>
<string name="action_forward_15s">Înainte cu 15 secunde</string>
<string name="action_reset_playback_speed">Resetează viteza de redare la 1x</string>
<string name="action_rewind_15s">Înapoi cu 15 secunde</string>
<string name="action_scroll_to_top">Derulează sus de tot</string>
<string name="dialog_replace_recording_message">\"%1$s\" se înregistrează în prezent. Doriți să opriți și să salvați înregistrarea curentă și să începeți înregistrarea \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Înregistrare în curs</string>
<string name="filter_all_stations">Toate posturile</string>
<string name="filter_date_any">Orice dată</string>

[values-ru]
<string name="accessibility_filter_date_from">С даты: %1$s</string>
<string name="accessibility_filter_date_to">По дату: %1$s</string>
<string name="accessibility_filter_station">Фильтр станций: %1$s</string>
<string name="accessibility_playback_speed">Скорость воспроизведения %1$s</string>
<string name="accessibility_seek_slider">Позиция воспроизведения</string>
<string name="accessibility_time_position">%1$s из %2$s</string>
<string name="action_change_playback_speed">Изменить скорость воспроизведения</string>
<string name="action_clear_date_filter">Сбросить фильтр даты</string>
<string name="action_clear_station_filter">Сбросить фильтр станций</string>
<string name="action_forward_15s">Вперёд на 15 секунд</string>
<string name="action_reset_playback_speed">Сбросить скорость воспроизведения до 1x</string>
<string name="action_rewind_15s">Назад на 15 секунд</string>
<string name="action_scroll_to_top">Прокрутить наверх</string>
<string name="dialog_replace_recording_message">Сейчас записывается «%1$s». Остановить и сохранить текущую запись и начать запись «%2$s»?</string>
<string name="dialog_replace_recording_title">Идёт запись</string>
<string name="filter_all_stations">Все станции</string>
<string name="filter_date_any">Любая дата</string>

[values-si]
<string name="accessibility_filter_date_from">දින සිට: %1$s</string>
<string name="accessibility_filter_date_to">දින දක්වා: %1$s</string>
<string name="accessibility_filter_station">නාලිකා පෙරහන: %1$s</string>
<string name="accessibility_playback_speed">ධාවන වේගය %1$s</string>
<string name="accessibility_seek_slider">ධාවන ස්ථානය</string>
<string name="accessibility_time_position">%2$s න් %1$s</string>
<string name="action_change_playback_speed">ධාවන වේගය වෙනස් කරන්න</string>
<string name="action_clear_date_filter">දින පෙරහන ඉවත් කරන්න</string>
<string name="action_clear_station_filter">නාලිකා පෙරහන ඉවත් කරන්න</string>
<string name="action_forward_15s">තත්පර 15ක් ඉදිරියට</string>
<string name="action_reset_playback_speed">ධාවන වේගය 1x වෙත යළි සකසන්න</string>
<string name="action_rewind_15s">තත්පර 15ක් පසුපසට</string>
<string name="action_scroll_to_top">ඉහළටම අනුචලනය කරන්න</string>
<string name="dialog_replace_recording_message">\"%1$s\" දැනට පටිගත වෙමින් පවතී. වත්මන් පටිගත කිරීම නවතා සුරැකීමට සහ \"%2$s\" පටිගත කිරීම ආරම්භ කිරීමට ඔබට අවශ්‍යද?</string>
<string name="dialog_replace_recording_title">පටිගත කිරීම සිදු වෙමින් පවතී</string>
<string name="filter_all_stations">සියලුම නාලිකා</string>
<string name="filter_date_any">ඕනෑම දිනයක්</string>

[values-sk]
<string name="accessibility_filter_date_from">Od dátumu: %1$s</string>
<string name="accessibility_filter_date_to">Do dátumu: %1$s</string>
<string name="accessibility_filter_station">Filter staníc: %1$s</string>
<string name="accessibility_playback_speed">Rýchlosť prehrávania %1$s</string>
<string name="accessibility_seek_slider">Pozícia prehrávania</string>
<string name="accessibility_time_position">%1$s z %2$s</string>
<string name="action_change_playback_speed">Zmeniť rýchlosť prehrávania</string>
<string name="action_clear_date_filter">Vymazať filter dátumu</string>
<string name="action_clear_station_filter">Vymazať filter staníc</string>
<string name="action_forward_15s">Dopredu o 15 sekúnd</string>
<string name="action_reset_playback_speed">Obnoviť rýchlosť prehrávania na 1x</string>
<string name="action_rewind_15s">Dozadu o 15 sekúnd</string>
<string name="action_scroll_to_top">Posunúť na začiatok</string>
<string name="dialog_replace_recording_message">Stanica \"%1$s\" sa práve nahráva. Chcete zastaviť a uložiť aktuálnu nahrávku a spustiť nahrávanie \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Prebieha nahrávanie</string>
<string name="filter_all_stations">Všetky stanice</string>
<string name="filter_date_any">Akýkoľvek dátum</string>

[values-sl]
<string name="accessibility_filter_date_from">Od datuma: %1$s</string>
<string name="accessibility_filter_date_to">Do datuma: %1$s</string>
<string name="accessibility_filter_station">Filter postaje: %1$s</string>
<string name="accessibility_playback_speed">Hitrost predvajanja %1$s</string>
<string name="accessibility_seek_slider">Položaj predvajanja</string>
<string name="accessibility_time_position">%1$s od %2$s</string>
<string name="action_change_playback_speed">Spremeni hitrost predvajanja</string>
<string name="action_clear_date_filter">Počisti filter datuma</string>
<string name="action_clear_station_filter">Počisti filter postaje</string>
<string name="action_forward_15s">Naprej za 15 sekund</string>
<string name="action_reset_playback_speed">Ponastavi hitrost predvajanja na 1x</string>
<string name="action_rewind_15s">Nazaj za 15 sekund</string>
<string name="action_scroll_to_top">Pomakni na vrh</string>
<string name="dialog_replace_recording_message">\"%1$s\" se trenutno snema. Ali želite ustaviti in shraniti trenutni posnetek ter začeti snemati \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Snemanje poteka</string>
<string name="filter_all_stations">Vse postaje</string>
<string name="filter_date_any">Kateri koli datum</string>

[values-sq]
<string name="accessibility_filter_date_from">Nga data: %1$s</string>
<string name="accessibility_filter_date_to">Deri më datën: %1$s</string>
<string name="accessibility_filter_station">Filtri i stacionit: %1$s</string>
<string name="accessibility_playback_speed">Shpejtësia e luajtjes %1$s</string>
<string name="accessibility_seek_slider">Pozicioni i luajtjes</string>
<string name="accessibility_time_position">%1$s nga %2$s</string>
<string name="action_change_playback_speed">Ndrysho shpejtësinë e luajtjes</string>
<string name="action_clear_date_filter">Pastro filtrin e datës</string>
<string name="action_clear_station_filter">Pastro filtrin e stacionit</string>
<string name="action_forward_15s">Përpara 15 sekonda</string>
<string name="action_reset_playback_speed">Rivendos shpejtësinë e luajtjes në 1x</string>
<string name="action_rewind_15s">Prapa 15 sekonda</string>
<string name="action_scroll_to_top">Lëviz në krye</string>
<string name="dialog_replace_recording_message">\"%1$s\" po regjistrohet aktualisht. Dëshiron të ndalosh dhe të ruash regjistrimin aktual dhe të fillosh regjistrimin e \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Regjistrimi në vazhdim</string>
<string name="filter_all_stations">Të gjitha stacionet</string>
<string name="filter_date_any">Çdo datë</string>

[values-sr]
<string name="accessibility_filter_date_from">Од датума: %1$s</string>
<string name="accessibility_filter_date_to">До датума: %1$s</string>
<string name="accessibility_filter_station">Филтер станице: %1$s</string>
<string name="accessibility_playback_speed">Брзина репродукције %1$s</string>
<string name="accessibility_seek_slider">Положај репродукције</string>
<string name="accessibility_time_position">%1$s од %2$s</string>
<string name="action_change_playback_speed">Промени брзину репродукције</string>
<string name="action_clear_date_filter">Обриши филтер датума</string>
<string name="action_clear_station_filter">Обриши филтер станице</string>
<string name="action_forward_15s">Напред 15 секунди</string>
<string name="action_reset_playback_speed">Врати брзину репродукције на 1x</string>
<string name="action_rewind_15s">Назад 15 секунди</string>
<string name="action_scroll_to_top">Помери на врх</string>
<string name="dialog_replace_recording_message">„%1$s“ се тренутно снима. Да ли желите да зауставите и сачувате тренутни снимак и започнете снимање „%2$s“?</string>
<string name="dialog_replace_recording_title">Снимање је у току</string>
<string name="filter_all_stations">Све станице</string>
<string name="filter_date_any">Било који датум</string>

[values-sv]
<string name="accessibility_filter_date_from">Från datum: %1$s</string>
<string name="accessibility_filter_date_to">Till datum: %1$s</string>
<string name="accessibility_filter_station">Stationsfilter: %1$s</string>
<string name="accessibility_playback_speed">Uppspelningshastighet %1$s</string>
<string name="accessibility_seek_slider">Uppspelningsposition</string>
<string name="accessibility_time_position">%1$s av %2$s</string>
<string name="action_change_playback_speed">Ändra uppspelningshastighet</string>
<string name="action_clear_date_filter">Rensa datumfilter</string>
<string name="action_clear_station_filter">Rensa stationsfilter</string>
<string name="action_forward_15s">Framåt 15 sekunder</string>
<string name="action_reset_playback_speed">Återställ uppspelningshastighet till 1x</string>
<string name="action_rewind_15s">Bakåt 15 sekunder</string>
<string name="action_scroll_to_top">Bläddra överst</string>
<string name="dialog_replace_recording_message">\"%1$s\" spelas in just nu. Vill du stoppa och spara den pågående inspelningen och börja spela in \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Inspelning pågår</string>
<string name="filter_all_stations">Alla stationer</string>
<string name="filter_date_any">Valfritt datum</string>

[values-sw]
<string name="accessibility_filter_date_from">Kuanzia tarehe: %1$s</string>
<string name="accessibility_filter_date_to">Hadi tarehe: %1$s</string>
<string name="accessibility_filter_station">Kichujio cha kituo: %1$s</string>
<string name="accessibility_playback_speed">Kasi ya uchezaji %1$s</string>
<string name="accessibility_seek_slider">Nafasi ya uchezaji</string>
<string name="accessibility_time_position">%1$s kati ya %2$s</string>
<string name="action_change_playback_speed">Badilisha kasi ya uchezaji</string>
<string name="action_clear_date_filter">Futa kichujio cha tarehe</string>
<string name="action_clear_station_filter">Futa kichujio cha kituo</string>
<string name="action_forward_15s">Sogeza mbele sekunde 15</string>
<string name="action_reset_playback_speed">Weka upya kasi ya uchezaji kuwa 1x</string>
<string name="action_rewind_15s">Rudisha nyuma sekunde 15</string>
<string name="action_scroll_to_top">Sogeza juu kabisa</string>
<string name="dialog_replace_recording_message">\"%1$s\" inarekodiwa kwa sasa. Je, ungependa kusitisha na kuhifadhi rekodi ya sasa kisha uanze kurekodi \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Kurekodi kunaendelea</string>
<string name="filter_all_stations">Vituo vyote</string>
<string name="filter_date_any">Tarehe yoyote</string>

[values-ta]
<string name="accessibility_filter_date_from">தேதி முதல்: %1$s</string>
<string name="accessibility_filter_date_to">தேதி வரை: %1$s</string>
<string name="accessibility_filter_station">நிலைய வடிகட்டி: %1$s</string>
<string name="accessibility_playback_speed">இயக்க வேகம் %1$s</string>
<string name="accessibility_seek_slider">இயக்க நிலை</string>
<string name="accessibility_time_position">%2$s இல் %1$s</string>
<string name="action_change_playback_speed">இயக்க வேகத்தை மாற்று</string>
<string name="action_clear_date_filter">தேதி வடிகட்டியை அழி</string>
<string name="action_clear_station_filter">நிலைய வடிகட்டியை அழி</string>
<string name="action_forward_15s">15 வினாடிகள் முன்னோக்கி</string>
<string name="action_reset_playback_speed">இயக்க வேகத்தை 1x ஆக மீட்டமை</string>
<string name="action_rewind_15s">15 வினாடிகள் பின்னோக்கி</string>
<string name="action_scroll_to_top">மேலே உருட்டு</string>
<string name="dialog_replace_recording_message">\"%1$s\" தற்போது பதிவு செய்யப்படுகிறது. தற்போதைய பதிவை நிறுத்தி சேமித்துவிட்டு \"%2$s\" பதிவைத் தொடங்க விரும்புகிறீர்களா?</string>
<string name="dialog_replace_recording_title">பதிவு நடைபெறுகிறது</string>
<string name="filter_all_stations">அனைத்து நிலையங்களும்</string>
<string name="filter_date_any">எந்தத் தேதியும்</string>

[values-te]
<string name="accessibility_filter_date_from">తేదీ నుండి: %1$s</string>
<string name="accessibility_filter_date_to">తేదీ వరకు: %1$s</string>
<string name="accessibility_filter_station">స్టేషన్ ఫిల్టర్: %1$s</string>
<string name="accessibility_playback_speed">ప్లేబ్యాక్ వేగం %1$s</string>
<string name="accessibility_seek_slider">ప్లేబ్యాక్ స్థానం</string>
<string name="accessibility_time_position">%2$s లో %1$s</string>
<string name="action_change_playback_speed">ప్లేబ్యాక్ వేగాన్ని మార్చండి</string>
<string name="action_clear_date_filter">తేదీ ఫిల్టర్‌ను క్లియర్ చేయండి</string>
<string name="action_clear_station_filter">స్టేషన్ ఫిల్టర్‌ను క్లియర్ చేయండి</string>
<string name="action_forward_15s">15 సెకన్లు ముందుకు</string>
<string name="action_reset_playback_speed">ప్లేబ్యాక్ వేగాన్ని 1x కి రీసెట్ చేయండి</string>
<string name="action_rewind_15s">15 సెకన్లు వెనుకకు</string>
<string name="action_scroll_to_top">పైకి స్క్రోల్ చేయండి</string>
<string name="dialog_replace_recording_message">\"%1$s\" ప్రస్తుతం రికార్డ్ అవుతోంది. మీరు ప్రస్తుత రికార్డింగ్‌ను ఆపి సేవ్ చేసి, \"%2$s\" రికార్డింగ్ ప్రారంభించాలనుకుంటున్నారా?</string>
<string name="dialog_replace_recording_title">రికార్డింగ్ జరుగుతోంది</string>
<string name="filter_all_stations">అన్ని స్టేషన్లు</string>
<string name="filter_date_any">ఏదైనా తేదీ</string>

[values-th]
<string name="accessibility_filter_date_from">ตั้งแต่วันที่: %1$s</string>
<string name="accessibility_filter_date_to">ถึงวันที่: %1$s</string>
<string name="accessibility_filter_station">ตัวกรองสถานี: %1$s</string>
<string name="accessibility_playback_speed">ความเร็วในการเล่น %1$s</string>
<string name="accessibility_seek_slider">ตำแหน่งการเล่น</string>
<string name="accessibility_time_position">%1$s จาก %2$s</string>
<string name="action_change_playback_speed">เปลี่ยนความเร็วในการเล่น</string>
<string name="action_clear_date_filter">ล้างตัวกรองวันที่</string>
<string name="action_clear_station_filter">ล้างตัวกรองสถานี</string>
<string name="action_forward_15s">เดินหน้า 15 วินาที</string>
<string name="action_reset_playback_speed">รีเซ็ตความเร็วในการเล่นเป็น 1x</string>
<string name="action_rewind_15s">ย้อนกลับ 15 วินาที</string>
<string name="action_scroll_to_top">เลื่อนไปด้านบนสุด</string>
<string name="dialog_replace_recording_message">กำลังบันทึก \"%1$s\" อยู่ในขณะนี้ คุณต้องการหยุดและบันทึกไฟล์ปัจจุบัน แล้วเริ่มบันทึก \"%2$s\" หรือไม่?</string>
<string name="dialog_replace_recording_title">กำลังบันทึกเสียง</string>
<string name="filter_all_stations">ทุกสถานี</string>
<string name="filter_date_any">ทุกวันที่</string>

[values-tr]
<string name="accessibility_filter_date_from">Başlangıç tarihi: %1$s</string>
<string name="accessibility_filter_date_to">Bitiş tarihi: %1$s</string>
<string name="accessibility_filter_station">İstasyon filtresi: %1$s</string>
<string name="accessibility_playback_speed">Oynatma hızı %1$s</string>
<string name="accessibility_seek_slider">Oynatma konumu</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Oynatma hızını değiştir</string>
<string name="action_clear_date_filter">Tarih filtresini temizle</string>
<string name="action_clear_station_filter">İstasyon filtresini temizle</string>
<string name="action_forward_15s">15 saniye ileri sar</string>
<string name="action_reset_playback_speed">Oynatma hızını 1x olarak sıfırla</string>
<string name="action_rewind_15s">15 saniye geri sar</string>
<string name="action_scroll_to_top">En üste kaydır</string>
<string name="dialog_replace_recording_message">\"%1$s\" şu anda kaydediliyor. Mevcut kaydı durdurup kaydetmek ve \"%2$s\" kaydını başlatmak istiyor musunuz?</string>
<string name="dialog_replace_recording_title">Kayıt devam ediyor</string>
<string name="filter_all_stations">Tüm istasyonlar</string>
<string name="filter_date_any">Herhangi bir tarih</string>

[values-uk]
<string name="accessibility_filter_date_from">Від дати: %1$s</string>
<string name="accessibility_filter_date_to">До дати: %1$s</string>
<string name="accessibility_filter_station">Фільтр станцій: %1$s</string>
<string name="accessibility_playback_speed">Швидкість відтворення %1$s</string>
<string name="accessibility_seek_slider">Позиція відтворення</string>
<string name="accessibility_time_position">%1$s із %2$s</string>
<string name="action_change_playback_speed">Змінити швидкість відтворення</string>
<string name="action_clear_date_filter">Очистити фільтр дати</string>
<string name="action_clear_station_filter">Очистити фільтр станцій</string>
<string name="action_forward_15s">Вперед на 15 секунд</string>
<string name="action_reset_playback_speed">Скинути швидкість відтворення до 1x</string>
<string name="action_rewind_15s">Назад на 15 секунд</string>
<string name="action_scroll_to_top">Прокрутити на початок</string>
<string name="dialog_replace_recording_message">Зараз записується «%1$s». Зупинити та зберегти поточний запис і почати запис «%2$s»?</string>
<string name="dialog_replace_recording_title">Триває запис</string>
<string name="filter_all_stations">Усі станції</string>
<string name="filter_date_any">Будь-яка дата</string>

[values-ur]
<string name="accessibility_filter_date_from">تاریخ سے: %1$s</string>
<string name="accessibility_filter_date_to">تاریخ تک: %1$s</string>
<string name="accessibility_filter_station">اسٹیشن فلٹر: %1$s</string>
<string name="accessibility_playback_speed">پلے بیک رفتار %1$s</string>
<string name="accessibility_seek_slider">پلے بیک پوزیشن</string>
<string name="accessibility_time_position">%2$s میں سے %1$s</string>
<string name="action_change_playback_speed">پلے بیک رفتار تبدیل کریں</string>
<string name="action_clear_date_filter">تاریخ کا فلٹر صاف کریں</string>
<string name="action_clear_station_filter">اسٹیشن فلٹر صاف کریں</string>
<string name="action_forward_15s">15 سیکنڈ آگے</string>
<string name="action_reset_playback_speed">پلے بیک رفتار 1x پر ری سیٹ کریں</string>
<string name="action_rewind_15s">15 سیکنڈ پیچھے</string>
<string name="action_scroll_to_top">سب سے اوپر سکرول کریں</string>
<string name="dialog_replace_recording_message">\"%1$s\" اس وقت ریکارڈ ہو رہا ہے۔ کیا آپ موجودہ ریکارڈنگ روک کر محفوظ کرنا اور \"%2$s\" کی ریکارڈنگ شروع کرنا چاہتے ہیں؟</string>
<string name="dialog_replace_recording_title">ریکارڈنگ جاری ہے</string>
<string name="filter_all_stations">تمام اسٹیشنز</string>
<string name="filter_date_any">کوئی بھی تاریخ</string>

[values-uz]
<string name="accessibility_filter_date_from">Sanadan: %1$s</string>
<string name="accessibility_filter_date_to">Sanagacha: %1$s</string>
<string name="accessibility_filter_station">Stansiya filtri: %1$s</string>
<string name="accessibility_playback_speed">Ijro tezligi %1$s</string>
<string name="accessibility_seek_slider">Ijro holati</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Ijro tezligini o‘zgartirish</string>
<string name="action_clear_date_filter">Sana filtrini tozalash</string>
<string name="action_clear_station_filter">Stansiya filtrini tozalash</string>
<string name="action_forward_15s">15 soniya oldinga</string>
<string name="action_reset_playback_speed">Ijro tezligini 1x ga tiklash</string>
<string name="action_rewind_15s">15 soniya orqaga</string>
<string name="action_scroll_to_top">Eng yuqoriga aylantirish</string>
<string name="dialog_replace_recording_message">Hozirda \"%1$s\" yozib olinmoqda. Joriy yozuvni to‘xtatib, saqlashni va \"%2$s\" yozishni boshlashni xohlaysizmi?</string>
<string name="dialog_replace_recording_title">Yozib olinmoqda</string>
<string name="filter_all_stations">Barcha stansiyalar</string>
<string name="filter_date_any">Istalgan sana</string>

[values-vi]
<string name="accessibility_filter_date_from">Từ ngày: %1$s</string>
<string name="accessibility_filter_date_to">Đến ngày: %1$s</string>
<string name="accessibility_filter_station">Bộ lọc đài: %1$s</string>
<string name="accessibility_playback_speed">Tốc độ phát %1$s</string>
<string name="accessibility_seek_slider">Vị trí phát</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">Thay đổi tốc độ phát</string>
<string name="action_clear_date_filter">Xóa bộ lọc ngày</string>
<string name="action_clear_station_filter">Xóa bộ lọc đài</string>
<string name="action_forward_15s">Tua tới 15 giây</string>
<string name="action_reset_playback_speed">Đặt lại tốc độ phát về 1x</string>
<string name="action_rewind_15s">Tua lại 15 giây</string>
<string name="action_scroll_to_top">Cuộn lên đầu</string>
<string name="dialog_replace_recording_message">\"%1$s\" hiện đang được ghi âm. Bạn có muốn dừng và lưu bản ghi hiện tại rồi bắt đầu ghi \"%2$s\" không?</string>
<string name="dialog_replace_recording_title">Đang ghi âm</string>
<string name="filter_all_stations">Tất cả các đài</string>
<string name="filter_date_any">Bất kỳ ngày nào</string>

[values-xh]
<string name="accessibility_filter_date_from">Ukususela kumhla: %1$s</string>
<string name="accessibility_filter_date_to">Ukuya kumhla: %1$s</string>
<string name="accessibility_filter_station">Isihluzi sesitishi: %1$s</string>
<string name="accessibility_playback_speed">Isantya sokudlala %1$s</string>
<string name="accessibility_seek_slider">Indawo yokudlala</string>
<string name="accessibility_time_position">%1$s kwi-%2$s</string>
<string name="action_change_playback_speed">Tshintsha isantya sokudlala</string>
<string name="action_clear_date_filter">Susa isihluzi somhla</string>
<string name="action_clear_station_filter">Susa isihluzi sesitishi</string>
<string name="action_forward_15s">Phambili ngemizuzwana eyi-15</string>
<string name="action_reset_playback_speed">Buyisela isantya sokudlala ku-1x</string>
<string name="action_rewind_15s">Emva ngemizuzwana eyi-15</string>
<string name="action_scroll_to_top">Skrolela phezulu</string>
<string name="dialog_replace_recording_message">\"%1$s\" iyarekhodwa ngoku. Ngaba ufuna ukumisa uze ugcine urekhodo lwangoku kwaye uqalise ukurekhoda \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Ukurekhoda kuyaqhubeka</string>
<string name="filter_all_stations">Zonke izitishi</string>
<string name="filter_date_any">Nawuphi na umhla</string>

[values-yi]
<string name="accessibility_filter_date_from">פֿון דאַטע: %1$s</string>
<string name="accessibility_filter_date_to">ביז דאַטע: %1$s</string>
<string name="accessibility_filter_station">סטאַנציע פֿילטער: %1$s</string>
<string name="accessibility_playback_speed">שפּיל-גיכקייט %1$s</string>
<string name="accessibility_seek_slider">שפּיל-פּאָזיציע</string>
<string name="accessibility_time_position">%1$s פֿון %2$s</string>
<string name="action_change_playback_speed">ענדערן שפּיל-גיכקייט</string>
<string name="action_clear_date_filter">אויסמעקן דאַטע פֿילטער</string>
<string name="action_clear_station_filter">אויסמעקן סטאַנציע פֿילטער</string>
<string name="action_forward_15s">פֿאָרויס 15 סעקונדעס</string>
<string name="action_reset_playback_speed">צוריקשטעלן שפּיל-גיכקייט צו 1x</string>
<string name="action_rewind_15s">צוריק 15 סעקונדעס</string>
<string name="action_scroll_to_top">סקראָלן צום שפּיץ</string>
<string name="dialog_replace_recording_message">\"%1$s\" ווערט איצט רעקאָרדירט. ווילט איר אָפּשטעלן און היטן די איצטיקע רעקאָרדירונג און אָנהייבן רעקאָרדירן \"%2$s\"?</string>
<string name="dialog_replace_recording_title">רעקאָרדירונג אין גאַנג</string>
<string name="filter_all_stations">אַלע סטאַנציעס</string>
<string name="filter_date_any">יעדע דאַטע</string>

[values-yo]
<string name="accessibility_filter_date_from">Lati ọjọ: %1$s</string>
<string name="accessibility_filter_date_to">Titi di ọjọ: %1$s</string>
<string name="accessibility_filter_station">Àlẹ̀mọ́ ibùdó: %1$s</string>
<string name="accessibility_playback_speed">Iyára ṣíṣẹ̀ %1$s</string>
<string name="accessibility_seek_slider">Ipò ṣíṣẹ̀</string>
<string name="accessibility_time_position">%1$s nínú %2$s</string>
<string name="action_change_playback_speed">Yí iyára ṣíṣẹ̀ padà</string>
<string name="action_clear_date_filter">Pa àlẹ̀mọ́ ọjọ́ rẹ́</string>
<string name="action_clear_station_filter">Pa àlẹ̀mọ́ ibùdó rẹ́</string>
<string name="action_forward_15s">Síwájú ìṣẹ́jú-àáyá 15</string>
<string name="action_reset_playback_speed">Tún iyára ṣíṣẹ̀ ṣe sí 1x</string>
<string name="action_rewind_15s">Padà sẹ́yìn ìṣẹ́jú-àáyá 15</string>
<string name="action_scroll_to_top">Yí lọ sí òkè pátápátá</string>
<string name="dialog_replace_recording_message">\"%1$s\" ń gba ohùn sílẹ̀ lọ́wọ́lọ́wọ́. Ṣé o fẹ́ dáwọ́ dúró kí o sì fi ìgbasílẹ̀ lọ́wọ́lọ́wọ́ pamọ́ kí o sì bẹ̀rẹ̀ sí í gba \"%2$s\" sílẹ̀?</string>
<string name="dialog_replace_recording_title">Ìgbasílẹ̀ ń lọ lọ́wọ́</string>
<string name="filter_all_stations">Gbogbo ibùdó</string>
<string name="filter_date_any">Ọjọ́ èyíkéyìí</string>

[values-zh-rCN]
<string name="accessibility_filter_date_from">开始日期：%1$s</string>
<string name="accessibility_filter_date_to">结束日期：%1$s</string>
<string name="accessibility_filter_station">电台筛选：%1$s</string>
<string name="accessibility_playback_speed">播放速度 %1$s</string>
<string name="accessibility_seek_slider">播放位置</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">更改播放速度</string>
<string name="action_clear_date_filter">清除日期筛选</string>
<string name="action_clear_station_filter">清除电台筛选</string>
<string name="action_forward_15s">快进 15 秒</string>
<string name="action_reset_playback_speed">将播放速度重置为 1x</string>
<string name="action_rewind_15s">快退 15 秒</string>
<string name="action_scroll_to_top">滚动到顶部</string>
<string name="dialog_replace_recording_message">当前正在录制“%1$s”。是否停止并保存当前录音，然后开始录制“%2$s”？</string>
<string name="dialog_replace_recording_title">正在录音</string>
<string name="filter_all_stations">所有电台</string>
<string name="filter_date_any">任意日期</string>

[values-zh-rHK]
<string name="accessibility_filter_date_from">開始日期：%1$s</string>
<string name="accessibility_filter_date_to">結束日期：%1$s</string>
<string name="accessibility_filter_station">電台篩選：%1$s</string>
<string name="accessibility_playback_speed">播放速度 %1$s</string>
<string name="accessibility_seek_slider">播放位置</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">變更播放速度</string>
<string name="action_clear_date_filter">清除日期篩選</string>
<string name="action_clear_station_filter">清除電台篩選</string>
<string name="action_forward_15s">快轉 15 秒</string>
<string name="action_reset_playback_speed">將播放速度重設為 1x</string>
<string name="action_rewind_15s">倒轉 15 秒</string>
<string name="action_scroll_to_top">捲動到頂部</string>
<string name="dialog_replace_recording_message">目前正在錄製「%1$s」。是否停止並儲存目前錄音，然後開始錄製「%2$s」？</string>
<string name="dialog_replace_recording_title">正在錄音</string>
<string name="filter_all_stations">所有電台</string>
<string name="filter_date_any">任何日期</string>

[values-zh-rTW]
<string name="accessibility_filter_date_from">開始日期：%1$s</string>
<string name="accessibility_filter_date_to">結束日期：%1$s</string>
<string name="accessibility_filter_station">電台篩選：%1$s</string>
<string name="accessibility_playback_speed">播放速度 %1$s</string>
<string name="accessibility_seek_slider">播放位置</string>
<string name="accessibility_time_position">%1$s / %2$s</string>
<string name="action_change_playback_speed">變更播放速度</string>
<string name="action_clear_date_filter">清除日期篩選</string>
<string name="action_clear_station_filter">清除電台篩選</string>
<string name="action_forward_15s">快轉 15 秒</string>
<string name="action_reset_playback_speed">將播放速度重設為 1x</string>
<string name="action_rewind_15s">倒轉 15 秒</string>
<string name="action_scroll_to_top">捲動到頂部</string>
<string name="dialog_replace_recording_message">目前正在錄製「%1$s」。是否停止並儲存目前錄音，然後開始錄製「%2$s」？</string>
<string name="dialog_replace_recording_title">正在錄音</string>
<string name="filter_all_stations">所有電台</string>
<string name="filter_date_any">任何日期</string>

[values-zu]
<string name="accessibility_filter_date_from">Kusuka kudethi: %1$s</string>
<string name="accessibility_filter_date_to">Kuze kube kudethi: %1$s</string>
<string name="accessibility_filter_station">Isihlungi sesiteshi: %1$s</string>
<string name="accessibility_playback_speed">Isivinini sokudlala %1$s</string>
<string name="accessibility_seek_slider">Indawo yokudlala</string>
<string name="accessibility_time_position">%1$s koku-%2$s</string>
<string name="action_change_playback_speed">Shintsha isivinini sokudlala</string>
<string name="action_clear_date_filter">Sula isihlungi sedethi</string>
<string name="action_clear_station_filter">Sula isihlungi sesiteshi</string>
<string name="action_forward_15s">Yisa phambili ngemizuzwana engu-15</string>
<string name="action_reset_playback_speed">Setha kabusha isivinini sokudlala sibe ngu-1x</string>
<string name="action_rewind_15s">Buyisela emuva ngemizuzwana engu-15</string>
<string name="action_scroll_to_top">Skrolela phezulu</string>
<string name="dialog_replace_recording_message">\"%1$s\" iyarekhodwa njengamanje. Ingabe ufuna ukumisa nokulondoloza okurekhodiwe kwamanje bese uqala ukurekhoda \"%2$s\"?</string>
<string name="dialog_replace_recording_title">Ukurekhoda kuyaqhubeka</string>
<string name="filter_all_stations">Zonke iziteshi</string>
<string name="filter_date_any">Noma iyiphi idethi</string>
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