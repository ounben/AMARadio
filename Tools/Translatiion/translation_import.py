import os
import re

translations_data = """
[values-af]
<string name="accessibility_filter_playlist">Snitlysfilter: %1$s</string>
<string name="accessibility_playlist_delete">Vee %1$s uit</string>
<string name="accessibility_playlist_edit_stations">Wysig stasies in %1$s</string>
<string name="accessibility_playlist_rename">Hernoem %1$s</string>
<string name="action_clear_playlist_filter">Vee snitlysfilter uit</string>
<string name="action_manage_station_playlists">Voeg by snitlys</string>
<string name="action_remove_from_playlist">Verwyder uit \"%1$s\"</string>
<string name="action_scroll_to_bottom">Rol na onder</string>
<string name="favorite_list_add_stations">Voeg gunstelinge by</string>
<string name="favorite_list_all">Almal</string>
<string name="favorite_list_assign_title">Voeg by snitlyste</string>
<string name="favorite_list_create">Nuwe snitlys</string>
<string name="favorite_list_delete_message">Vee \"%1$s\" uit? Jou stasies sal in Gunstelinge bly.</string>
<string name="favorite_list_delete_title">Vee snitlys uit</string>
<string name="favorite_list_edit_stations">Wysig stasies</string>
<string name="favorite_list_empty">Hierdie snitlys is leeg</string>
<string name="favorite_list_manage">Bestuur snitlyste</string>
<string name="favorite_list_name_hint">Snitlysnaam</string>
<string name="favorite_list_none_yet">Nog geen snitlyste nie. Skep een om jou gunstelinge te organiseer.</string>
<string name="favorite_list_rename">Hernoem snitlys</string>

[values-am]
<string name="accessibility_filter_playlist">የአጫዋች ዝርዝር ማጣሪያ፦ %1$s</string>
<string name="accessibility_playlist_delete">%1$s ን ሰርዝ</string>
<string name="accessibility_playlist_edit_stations">በ%1$s ውስጥ ጣቢያዎችን አርትዕ</string>
<string name="accessibility_playlist_rename">%1$s ን እንደገና ሰይም</string>
<string name="action_clear_playlist_filter">የአጫዋች ዝርዝር ማጣሪያ አጽዳ</string>
<string name="action_manage_station_playlists">ወደ አጫዋች ዝርዝር አክል</string>
<string name="action_remove_from_playlist">ከ \"%1$s\" አስወግድ</string>
<string name="action_scroll_to_bottom">ወደ ታች ሸብልል</string>
<string name="favorite_list_add_stations">ተወዳጆችን አክል</string>
<string name="favorite_list_all">ሁሉም</string>
<string name="favorite_list_assign_title">ወደ አጫዋች ዝርዝሮች አክል</string>
<string name="favorite_list_create">አዲስ አጫዋች ዝርዝር</string>
<string name="favorite_list_delete_message">\"%1$s\" ይሰረዝ? ጣቢያዎችዎ በተወዳጆች ውስጥ ይቆያሉ።</string>
<string name="favorite_list_delete_title">አጫዋች ዝርዝር ሰርዝ</string>
<string name="favorite_list_edit_stations">ጣቢያዎችን አርትዕ</string>
<string name="favorite_list_empty">ይህ አጫዋች ዝርዝር ባዶ ነው</string>
<string name="favorite_list_manage">አጫዋች ዝርዝሮችን አስተዳድር</string>
<string name="favorite_list_name_hint">የአጫዋች ዝርዝር ስም</string>
<string name="favorite_list_none_yet">እስካሁን ምንም አጫዋች ዝርዝሮች የሉም። ተወዳጆችዎን ለማደራጀት አንድ ይፍጠሩ።</string>
<string name="favorite_list_rename">አጫዋች ዝርዝር እንደገና ሰይም</string>

[values-ar]
<string name="accessibility_filter_playlist">فلتر قائمة التشغيل: %1$s</string>
<string name="accessibility_playlist_delete">حذف %1$s</string>
<string name="accessibility_playlist_edit_stations">تعديل المحطات في %1$s</string>
<string name="accessibility_playlist_rename">إعادة تسمية %1$s</string>
<string name="action_clear_playlist_filter">مسح فلتر قائمة التشغيل</string>
<string name="action_manage_station_playlists">إضافة إلى قائمة التشغيل</string>
<string name="action_remove_from_playlist">إزالة من \"%1$s\"</string>
<string name="action_scroll_to_bottom">التمرير إلى الأسفل</string>
<string name="favorite_list_add_stations">إضافة مفضلات</string>
<string name="favorite_list_all">الكل</string>
<string name="favorite_list_assign_title">إضافة إلى قوائم التشغيل</string>
<string name="favorite_list_create">قائمة تشغيل جديدة</string>
<string name="favorite_list_delete_message">هل تريد حذف \"%1$s\"؟ ستبقى محطاتك في المفضلة.</string>
<string name="favorite_list_delete_title">حذف قائمة التشغيل</string>
<string name="favorite_list_edit_stations">تعديل المحطات</string>
<string name="favorite_list_empty">قائمة التشغيل هذه فارغة</string>
<string name="favorite_list_manage">إدارة قوائم التشغيل</string>
<string name="favorite_list_name_hint">اسم قائمة التشغيل</string>
<string name="favorite_list_none_yet">لا توجد قوائم تشغيل بعد. أنشئ واحدة لتنظيم مفضلاتك.</string>
<string name="favorite_list_rename">إعادة تسمية قائمة التشغيل</string>

[values-as]
<string name="accessibility_filter_playlist">প্লেলিষ্ট ফিল্টাৰ: %1$s</string>
<string name="accessibility_playlist_delete">%1$s মচক</string>
<string name="accessibility_playlist_edit_stations">%1$s-ত ষ্টেচন সম্পাদনা কৰক</string>
<string name="accessibility_playlist_rename">%1$s পুনঃনামকৰণ কৰক</string>
<string name="action_clear_playlist_filter">প্লেলিষ্ট ফিল্টাৰ মচক</string>
<string name="action_manage_station_playlists">প্লেলিষ্টত যোগ কৰক</string>
<string name="action_remove_from_playlist">\"%1$s\" ৰ পৰা আঁতৰাওক</string>
<string name="action_scroll_to_bottom">তললৈ স্ক্ৰল কৰক</string>
<string name="favorite_list_add_stations">প্ৰিয়সমূহ যোগ কৰক</string>
<string name="favorite_list_all">সকলো</string>
<string name="favorite_list_assign_title">প্লেলিষ্টত যোগ কৰক</string>
<string name="favorite_list_create">নতুন প্লেলিষ্ট</string>
<string name="favorite_list_delete_message">\"%1$s\" মচিব নেকি? আপোনাৰ ষ্টেচনসমূহ প্ৰিয়ত থাকিব।</string>
<string name="favorite_list_delete_title">প্লেলিষ্ট মচক</string>
<string name="favorite_list_edit_stations">ষ্টেচন সম্পাদনা কৰক</string>
<string name="favorite_list_empty">এই প্লেলিষ্ট খালী</string>
<string name="favorite_list_manage">প্লেলিষ্ট পৰিচালনা কৰক</string>
<string name="favorite_list_name_hint">প্লেলিষ্টৰ নাম</string>
<string name="favorite_list_none_yet">এতিয়াও কোনো প্লেলিষ্ট নাই। আপোনাৰ প্ৰিয়সমূহ সজাবলৈ এটা সৃষ্টি কৰক।</string>
<string name="favorite_list_rename">প্লেলিষ্ট পুনঃনামকৰণ কৰক</string>

[values-az]
<string name="accessibility_filter_playlist">Pleylist filtri: %1$s</string>
<string name="accessibility_playlist_delete">%1$s sil</string>
<string name="accessibility_playlist_edit_stations">%1$s daxilində stansiyaları redaktə et</string>
<string name="accessibility_playlist_rename">%1$s adını dəyiş</string>
<string name="action_clear_playlist_filter">Pleylist filtrini təmizlə</string>
<string name="action_manage_station_playlists">Pleylistə əlavə et</string>
<string name="action_remove_from_playlist">\"%1$s\" siyahısından sil</string>
<string name="action_scroll_to_bottom">Aşağı sürüşdür</string>
<string name="favorite_list_add_stations">Sevimliləri əlavə et</string>
<string name="favorite_list_all">Hamısı</string>
<string name="favorite_list_assign_title">Pleylistlərə əlavə et</string>
<string name="favorite_list_create">Yeni pleylist</string>
<string name="favorite_list_delete_message">\"%1$s\" silinsin? Stansiyalarınız Sevimlilərdə qalacaq.</string>
<string name="favorite_list_delete_title">Pleylisti sil</string>
<string name="favorite_list_edit_stations">Stansiyaları redaktə et</string>
<string name="favorite_list_empty">Bu pleylist boşdur</string>
<string name="favorite_list_manage">Pleylistləri idarə et</string>
<string name="favorite_list_name_hint">Pleylist adı</string>
<string name="favorite_list_none_yet">Hələ pleylist yoxdur. Sevimlilərinizi nizamlamaq üçün birini yaradın.</string>
<string name="favorite_list_rename">Pleylistin adını dəyiş</string>

[values-be]
<string name="accessibility_filter_playlist">Фільтр плэйліста: %1$s</string>
<string name="accessibility_playlist_delete">Выдаліць %1$s</string>
<string name="accessibility_playlist_edit_stations">Рэдагаваць станцыі ў %1$s</string>
<string name="accessibility_playlist_rename">Перайменаваць %1$s</string>
<string name="action_clear_playlist_filter">Ачысціць фільтр плэйліста</string>
<string name="action_manage_station_playlists">Дадаць у плэйліст</string>
<string name="action_remove_from_playlist">Выдаліць з \"%1$s\"</string>
<string name="action_scroll_to_bottom">Пракруціць уніз</string>
<string name="favorite_list_add_stations">Дадаць абранае</string>
<string name="favorite_list_all">Усе</string>
<string name="favorite_list_assign_title">Дадаць у плэйлісты</string>
<string name="favorite_list_create">Новы плэйліст</string>
<string name="favorite_list_delete_message">Выдаліць \"%1$s\"? Вашы станцыі застануцца ў Абраным.</string>
<string name="favorite_list_delete_title">Выдаліць плэйліст</string>
<string name="favorite_list_edit_stations">Рэдагаваць станцыі</string>
<string name="favorite_list_empty">Гэты плэйліст пусты</string>
<string name="favorite_list_manage">Кіраванне плэйлістамі</string>
<string name="favorite_list_name_hint">Назва плэйліста</string>
<string name="favorite_list_none_yet">Плэйлістоў пакуль няма. Стварыце адзін, каб упарадкаваць абранае.</string>
<string name="favorite_list_rename">Перайменаваць плэйліст</string>

[values-bg]
<string name="accessibility_filter_playlist">Филтър по плейлист: %1$s</string>
<string name="accessibility_playlist_delete">Изтриване на %1$s</string>
<string name="accessibility_playlist_edit_stations">Редактиране на станции в %1$s</string>
<string name="accessibility_playlist_rename">Преименуване на %1$s</string>
<string name="action_clear_playlist_filter">Изчистване на филтъра за плейлист</string>
<string name="action_manage_station_playlists">Добавяне към плейлист</string>
<string name="action_remove_from_playlist">Премахване от \"%1$s\"</string>
<string name="action_scroll_to_bottom">Превъртане най-отдолу</string>
<string name="favorite_list_add_stations">Добавяне на любими</string>
<string name="favorite_list_all">Всички</string>
<string name="favorite_list_assign_title">Добавяне към плейлисти</string>
<string name="favorite_list_create">Нов плейлист</string>
<string name="favorite_list_delete_message">Изтриване на \"%1$s\"? Вашите станции ще останат в Любими.</string>
<string name="favorite_list_delete_title">Изтриване на плейлист</string>
<string name="favorite_list_edit_stations">Редактиране на станции</string>
<string name="favorite_list_empty">Този плейлист е празен</string>
<string name="favorite_list_manage">Управление на плейлисти</string>
<string name="favorite_list_name_hint">Име на плейлиста</string>
<string name="favorite_list_none_yet">Все още няма плейлисти. Създайте такъв, за да организирате любимите си.</string>
<string name="favorite_list_rename">Преименуване на плейлиста</string>

[values-bn]
<string name="accessibility_filter_playlist">প্লেলিস্ট ফিল্টার: %1$s</string>
<string name="accessibility_playlist_delete">%1$s মুছুন</string>
<string name="accessibility_playlist_edit_stations">%1$s-এ স্টেশন সম্পাদনা করুন</string>
<string name="accessibility_playlist_rename">%1$s-এর নাম পরিবর্তন করুন</string>
<string name="action_clear_playlist_filter">প্লেলিস্ট ফিল্টার মুছুন</string>
<string name="action_manage_station_playlists">প্লেলিস্টে যোগ করুন</string>
<string name="action_remove_from_playlist">\"%1$s\" থেকে সরান</string>
<string name="action_scroll_to_bottom">সবার নিচে স্ক্রল করুন</string>
<string name="favorite_list_add_stations">পছন্দেরগুলো যোগ করুন</string>
<string name="favorite_list_all">সব</string>
<string name="favorite_list_assign_title">প্লেলিস্টে যোগ করুন</string>
<string name="favorite_list_create">নতুন প্লেলিস্ট</string>
<string name="favorite_list_delete_message">\"%1$s\" মুছে ফেলবেন? আপনার স্টেশনগুলো পছন্দের তালিকায় থাকবে।</string>
<string name="favorite_list_delete_title">প্লেলিস্ট মুছুন</string>
<string name="favorite_list_edit_stations">স্টেশন সম্পাদনা করুন</string>
<string name="favorite_list_empty">এই প্লেলিস্টটি খালি</string>
<string name="favorite_list_manage">প্লেলিস্ট পরিচালনা করুন</string>
<string name="favorite_list_name_hint">প্লেলিস্টের নাম</string>
<string name="favorite_list_none_yet">এখনও কোনো প্লেলিস্ট নেই। আপনার পছন্দের স্টেশনগুলো সাজাতে একটি তৈরি করুন।</string>
<string name="favorite_list_rename">প্লেলিস্টের নাম পরিবর্তন করুন</string>

[values-bs]
<string name="accessibility_filter_playlist">Filter plejliste: %1$s</string>
<string name="accessibility_playlist_delete">Izbriši %1$s</string>
<string name="accessibility_playlist_edit_stations">Uredi stanice u %1$s</string>
<string name="accessibility_playlist_rename">Preimenuj %1$s</string>
<string name="action_clear_playlist_filter">Obriši filter plejliste</string>
<string name="action_manage_station_playlists">Dodaj na plejlistu</string>
<string name="action_remove_from_playlist">Ukloni iz \"%1$s\"</string>
<string name="action_scroll_to_bottom">Pomjeri na dno</string>
<string name="favorite_list_add_stations">Dodaj favorite</string>
<string name="favorite_list_all">Sve</string>
<string name="favorite_list_assign_title">Dodaj na plejliste</string>
<string name="favorite_list_create">Nova plejlista</string>
<string name="favorite_list_delete_message">Izbrisati \"%1$s\"? Vaše stanice će ostati u Favoritima.</string>
<string name="favorite_list_delete_title">Izbriši plejlistu</string>
<string name="favorite_list_edit_stations">Uredi stanice</string>
<string name="favorite_list_empty">Ova plejlista je prazna</string>
<string name="favorite_list_manage">Upravljaj plejlistama</string>
<string name="favorite_list_name_hint">Naziv plejliste</string>
<string name="favorite_list_none_yet">Još nema plejlista. Kreirajte jednu da organizujete svoje favorite.</string>
<string name="favorite_list_rename">Preimenuj plejlistu</string>

[values-ca]
<string name="accessibility_filter_playlist">Filtre de llista de reproducció: %1$s</string>
<string name="accessibility_playlist_delete">Suprimeix %1$s</string>
<string name="accessibility_playlist_edit_stations">Edita emissores a %1$s</string>
<string name="accessibility_playlist_rename">Canvia el nom de %1$s</string>
<string name="action_clear_playlist_filter">Esborra el filtre de llista de reproducció</string>
<string name="action_manage_station_playlists">Afegeix a la llista de reproducció</string>
<string name="action_remove_from_playlist">Elimina de \"%1$s\"</string>
<string name="action_scroll_to_bottom">Desplaça\'t fins al final</string>
<string name="favorite_list_add_stations">Afegeix preferits</string>
<string name="favorite_list_all">Totes</string>
<string name="favorite_list_assign_title">Afegeix a llistes de reproducció</string>
<string name="favorite_list_create">Nova llista de reproducció</string>
<string name="favorite_list_delete_message">Vols suprimir \"%1$s\"? Les teves emissores romandran a Preferits.</string>
<string name="favorite_list_delete_title">Suprimeix la llista de reproducció</string>
<string name="favorite_list_edit_stations">Edita emissores</string>
<string name="favorite_list_empty">Aquesta llista de reproducció és buida</string>
<string name="favorite_list_manage">Gestiona les llistes de reproducció</string>
<string name="favorite_list_name_hint">Nom de la llista de reproducció</string>
<string name="favorite_list_none_yet">Encara no hi ha cap llista de reproducció. Crea\'n una per organitzar els teus preferits.</string>
<string name="favorite_list_rename">Canvia el nom de la llista de reproducció</string>

[values-cs]
<string name="accessibility_filter_playlist">Filtr seznamu skladeb: %1$s</string>
<string name="accessibility_playlist_delete">Smazat %1$s</string>
<string name="accessibility_playlist_edit_stations">Upravit stanice v %1$s</string>
<string name="accessibility_playlist_rename">Přejmenovat %1$s</string>
<string name="action_clear_playlist_filter">Vymazat filtr seznamu skladeb</string>
<string name="action_manage_station_playlists">Přidat do seznamu skladeb</string>
<string name="action_remove_from_playlist">Odebrat z \"%1$s\"</string>
<string name="action_scroll_to_bottom">Posunout dolů</string>
<string name="favorite_list_add_stations">Přidat oblíbené</string>
<string name="favorite_list_all">Vše</string>
<string name="favorite_list_assign_title">Přidat do seznamů skladeb</string>
<string name="favorite_list_create">Nový seznam skladeb</string>
<string name="favorite_list_delete_message">Smazat \"%1$s\"? Vaše stanice zůstanou v Oblíbených.</string>
<string name="favorite_list_delete_title">Smazat seznam skladeb</string>
<string name="favorite_list_edit_stations">Upravit stanice</string>
<string name="favorite_list_empty">Tento seznam skladeb je prázdný</string>
<string name="favorite_list_manage">Spravovat seznamy skladeb</string>
<string name="favorite_list_name_hint">Název seznamu skladeb</string>
<string name="favorite_list_none_yet">Zatím žádné seznamy skladeb. Vytvořte jeden pro uspořádání oblíbených.</string>
<string name="favorite_list_rename">Přejmenovat seznam skladeb</string>

[values-da]
<string name="accessibility_filter_playlist">Afspilningslistefilter: %1$s</string>
<string name="accessibility_playlist_delete">Slet %1$s</string>
<string name="accessibility_playlist_edit_stations">Rediger stationer i %1$s</string>
<string name="accessibility_playlist_rename">Omdøb %1$s</string>
<string name="action_clear_playlist_filter">Ryd afspilningslistefilter</string>
<string name="action_manage_station_playlists">Føj til afspilningsliste</string>
<string name="action_remove_from_playlist">Fjern fra \"%1$s\"</string>
<string name="action_scroll_to_bottom">Rul til bunden</string>
<string name="favorite_list_add_stations">Tilføj favoritter</string>
<string name="favorite_list_all">Alle</string>
<string name="favorite_list_assign_title">Føj til afspilningslister</string>
<string name="favorite_list_create">Ny afspilningsliste</string>
<string name="favorite_list_delete_message">Slet \"%1$s\"? Dine stationer forbliver i Favoritter.</string>
<string name="favorite_list_delete_title">Slet afspilningsliste</string>
<string name="favorite_list_edit_stations">Rediger stationer</string>
<string name="favorite_list_empty">Denne afspilningsliste er tom</string>
<string name="favorite_list_manage">Administrer afspilningslister</string>
<string name="favorite_list_name_hint">Navn på afspilningsliste</string>
<string name="favorite_list_none_yet">Ingen afspilningslister endnu. Opret en for at organisere dine favoritter.</string>
<string name="favorite_list_rename">Omdøb afspilningsliste</string>

[values-de]
<string name="accessibility_filter_playlist">Playlist-Filter: %1$s</string>
<string name="accessibility_playlist_delete">%1$s löschen</string>
<string name="accessibility_playlist_edit_stations">Sender in %1$s bearbeiten</string>
<string name="accessibility_playlist_rename">%1$s umbenennen</string>
<string name="action_clear_playlist_filter">Playlist-Filter löschen</string>
<string name="action_manage_station_playlists">Zur Playlist hinzufügen</string>
<string name="action_remove_from_playlist">Aus „%1$s“ entfernen</string>
<string name="action_scroll_to_bottom">Nach unten scrollen</string>
<string name="favorite_list_add_stations">Favoriten hinzufügen</string>
<string name="favorite_list_all">Alle</string>
<string name="favorite_list_assign_title">Zu Playlists hinzufügen</string>
<string name="favorite_list_create">Neue Playlist</string>
<string name="favorite_list_delete_message">„%1$s“ löschen? Ihre Sender bleiben in den Favoriten.</string>
<string name="favorite_list_delete_title">Playlist löschen</string>
<string name="favorite_list_edit_stations">Sender bearbeiten</string>
<string name="favorite_list_empty">Diese Playlist ist leer</string>
<string name="favorite_list_manage">Playlists verwalten</string>
<string name="favorite_list_name_hint">Playlist-Name</string>
<string name="favorite_list_none_yet">Noch keine Playlists. Erstellen Sie eine, um Ihre Favoriten zu organisieren.</string>
<string name="favorite_list_rename">Playlist umbenennen</string>

[values-el]
<string name="accessibility_filter_playlist">Φίλτρο λίστας αναπαραγωγής: %1$s</string>
<string name="accessibility_playlist_delete">Διαγραφή %1$s</string>
<string name="accessibility_playlist_edit_stations">Επεξεργασία σταθμών στο %1$s</string>
<string name="accessibility_playlist_rename">Μετονομασία %1$s</string>
<string name="action_clear_playlist_filter">Εκκαθάριση φίλτρου λίστας αναπαραγωγής</string>
<string name="action_manage_station_playlists">Προσθήκη στη λίστα αναπαραγωγής</string>
<string name="action_remove_from_playlist">Αφαίρεση από \"%1$s\"</string>
<string name="action_scroll_to_bottom">Κύλιση στο κάτω μέρος</string>
<string name="favorite_list_add_stations">Προσθήκη αγαπημένων</string>
<string name="favorite_list_all">Όλα</string>
<string name="favorite_list_assign_title">Προσθήκη σε λίστες αναπαραγωγής</string>
<string name="favorite_list_create">Νέα λίστα αναπαραγωγής</string>
<string name="favorite_list_delete_message">Διαγραφή \"%1$s\"; Οι σταθμοί σας θα παραμείνουν στα Αγαπημένα.</string>
<string name="favorite_list_delete_title">Διαγραφή λίστας αναπαραγωγής</string>
<string name="favorite_list_edit_stations">Επεξεργασία σταθμών</string>
<string name="favorite_list_empty">Αυτή η λίστα αναπαραγωγής είναι κενή</string>
<string name="favorite_list_manage">Διαχείριση λιστών αναπαραγωγής</string>
<string name="favorite_list_name_hint">Όνομα λίστας αναπαραγωγής</string>
<string name="favorite_list_none_yet">Δεν υπάρχουν ακόμα λίστες αναπαραγωγής. Δημιουργήστε μία για να οργανώσετε τα αγαπημένα σας.</string>
<string name="favorite_list_rename">Μετονομασία λίστας αναπαραγωγής</string>

[values-es]
<string name="accessibility_filter_playlist">Filtro de lista de reproducción: %1$s</string>
<string name="accessibility_playlist_delete">Eliminar %1$s</string>
<string name="accessibility_playlist_edit_stations">Editar emisoras en %1$s</string>
<string name="accessibility_playlist_rename">Cambiar el nombre de %1$s</string>
<string name="action_clear_playlist_filter">Borrar filtro de lista de reproducción</string>
<string name="action_manage_station_playlists">Añadir a la lista de reproducción</string>
<string name="action_remove_from_playlist">Quitar de \"%1$s\"</string>
<string name="action_scroll_to_bottom">Desplazarse hacia abajo</string>
<string name="favorite_list_add_stations">Añadir favoritos</string>
<string name="favorite_list_all">Todos</string>
<string name="favorite_list_assign_title">Añadir a listas de reproducción</string>
<string name="favorite_list_create">Nueva lista de reproducción</string>
<string name="favorite_list_delete_message">¿Eliminar \"%1$s\"? Tus emisoras permanecerán en Favoritos.</string>
<string name="favorite_list_delete_title">Eliminar lista de reproducción</string>
<string name="favorite_list_edit_stations">Editar emisoras</string>
<string name="favorite_list_empty">Esta lista de reproducción está vacía</string>
<string name="favorite_list_manage">Gestionar listas de reproducción</string>
<string name="favorite_list_name_hint">Nombre de la lista de reproducción</string>
<string name="favorite_list_none_yet">Aún no hay listas de reproducción. Crea una para organizar tus favoritos.</string>
<string name="favorite_list_rename">Cambiar nombre de la lista de reproducción</string>

[values-et]
<string name="accessibility_filter_playlist">Esitusloendi filter: %1$s</string>
<string name="accessibility_playlist_delete">Kustuta %1$s</string>
<string name="accessibility_playlist_edit_stations">Muuda jaamu loendis %1$s</string>
<string name="accessibility_playlist_rename">Nimeta %1$s ümber</string>
<string name="action_clear_playlist_filter">Tühjenda esitusloendi filter</string>
<string name="action_manage_station_playlists">Lisa esitusloendisse</string>
<string name="action_remove_from_playlist">Eemalda loendist \"%1$s\"</string>
<string name="action_scroll_to_bottom">Keri alla</string>
<string name="favorite_list_add_stations">Lisa lemmikuid</string>
<string name="favorite_list_all">Kõik</string>
<string name="favorite_list_assign_title">Lisa esitusloenditesse</string>
<string name="favorite_list_create">Uus esitusloend</string>
<string name="favorite_list_delete_message">Kas kustutada \"%1$s\"? Teie jaamad jäävad lemmikute hulka.</string>
<string name="favorite_list_delete_title">Kustuta esitusloend</string>
<string name="favorite_list_edit_stations">Muuda jaamu</string>
<string name="favorite_list_empty">See esitusloend on tühi</string>
<string name="favorite_list_manage">Halda esitusloendeid</string>
<string name="favorite_list_name_hint">Esitusloendi nimi</string>
<string name="favorite_list_none_yet">Esitusloendeid pole veel. Looge üks oma lemmikute korrastamiseks.</string>
<string name="favorite_list_rename">Nimeta esitusloend ümber</string>

[values-eu]
<string name="accessibility_filter_playlist">Erreprodukzio-zerrendaren iragazkia: %1$s</string>
<string name="accessibility_playlist_delete">Ezabatu %1$s</string>
<string name="accessibility_playlist_edit_stations">Editatu irratiak hemen: %1$s</string>
<string name="accessibility_playlist_rename">Aldatu %1$s izena</string>
<string name="action_clear_playlist_filter">Garbitu erreprodukzio-zerrendaren iragazkia</string>
<string name="action_manage_station_playlists">Gehitu erreprodukzio-zerrendan</string>
<string name="action_remove_from_playlist">Kendu \"%1$s\" zerrendatik</string>
<string name="action_scroll_to_bottom">Joan behealdera</string>
<string name="favorite_list_add_stations">Gehitu gogokoak</string>
<string name="favorite_list_all">Denak</string>
<string name="favorite_list_assign_title">Gehitu erreprodukzio-zerrendetara</string>
<string name="favorite_list_create">Erreprodukzio-zerrenda berria</string>
<string name="favorite_list_delete_message">\"%1$s\" ezabatu? Zure irratiak Gogokoetan mantenduko dira.</string>
<string name="favorite_list_delete_title">Ezabatu erreprodukzio-zerrenda</string>
<string name="favorite_list_edit_stations">Editatu irratiak</string>
<string name="favorite_list_empty">Erreprodukzio-zerrenda hau hutsik dago</string>
<string name="favorite_list_manage">Kudeatu erreprodukzio-zerrendak</string>
<string name="favorite_list_name_hint">Erreprodukzio-zerrendaren izena</string>
<string name="favorite_list_none_yet">Oraindik ez dago erreprodukzio-zerrendarik. Sortu bat zure gogokoak antolatzeko.</string>
<string name="favorite_list_rename">Aldatu erreprodukzio-zerrendaren izena</string>

[values-fa]
<string name="accessibility_filter_playlist">فیلتر لیست پخش: %1$s</string>
<string name="accessibility_playlist_delete">حذف %1$s</string>
<string name="accessibility_playlist_edit_stations">ویرایش ایستگاه‌ها در %1$s</string>
<string name="accessibility_playlist_rename">تغییر نام %1$s</string>
<string name="action_clear_playlist_filter">پاک کردن فیلتر لیست پخش</string>
<string name="action_manage_station_playlists">افزودن به لیست پخش</string>
<string name="action_remove_from_playlist">حذف از «%1$s»</string>
<string name="action_scroll_to_bottom">اسکرول به پایین</string>
<string name="favorite_list_add_stations">افزودن علاقه‌مندی‌ها</string>
<string name="favorite_list_all">همه</string>
<string name="favorite_list_assign_title">افزودن به لیست‌های پخش</string>
<string name="favorite_list_create">لیست پخش جدید</string>
<string name="favorite_list_delete_message">«%1$s» حذف شود؟ ایستگاه‌های شما در علاقه‌مندی‌ها باقی خواهند ماند.</string>
<string name="favorite_list_delete_title">حذف لیست پخش</string>
<string name="favorite_list_edit_stations">ویرایش ایستگاه‌ها</string>
<string name="favorite_list_empty">این لیست پخش خالی است</string>
<string name="favorite_list_manage">مدیریت لیست‌های پخش</string>
<string name="favorite_list_name_hint">نام لیست پخش</string>
<string name="favorite_list_none_yet">هنوز لیست پخشی وجود ندارد. برای مرتب‌سازی علاقه‌مندی‌های خود یکی ایجاد کنید.</string>
<string name="favorite_list_rename">تغییر نام لیست پخش</string>

[values-fi]
<string name="accessibility_filter_playlist">Soittolistasuodatin: %1$s</string>
<string name="accessibility_playlist_delete">Poista %1$s</string>
<string name="accessibility_playlist_edit_stations">Muokkaa asemia kohteessa %1$s</string>
<string name="accessibility_playlist_rename">Nimeä %1$s uudelleen</string>
<string name="action_clear_playlist_filter">Tyhjennä soittolistasuodatin</string>
<string name="action_manage_station_playlists">Lisää soittolistaan</string>
<string name="action_remove_from_playlist">Poista soittolistalta \"%1$s\"</string>
<string name="action_scroll_to_bottom">Vieritä alas</string>
<string name="favorite_list_add_stations">Lisää suosikkeja</string>
<string name="favorite_list_all">Kaikki</string>
<string name="favorite_list_assign_title">Lisää soittolistoihin</string>
<string name="favorite_list_create">Uusi soittolista</string>
<string name="favorite_list_delete_message">Poistetaanko \"%1$s\"? Asemasi säilyvät Suosikeissa.</string>
<string name="favorite_list_delete_title">Poista soittolista</string>
<string name="favorite_list_edit_stations">Muokkaa asemia</string>
<string name="favorite_list_empty">Tämä soittolista on tyhjä</string>
<string name="favorite_list_manage">Hallitse soittolistoja</string>
<string name="favorite_list_name_hint">Soittolistan nimi</string>
<string name="favorite_list_none_yet">Ei vielä soittolistoja. Luo soittolista järjestääksesi suosikkisi.</string>
<string name="favorite_list_rename">Nimeä soittolista uudelleen</string>

[values-fil]
<string name="accessibility_filter_playlist">Filter ng playlist: %1$s</string>
<string name="accessibility_playlist_delete">I-delete ang %1$s</string>
<string name="accessibility_playlist_edit_stations">I-edit ang mga istasyon sa %1$s</string>
<string name="accessibility_playlist_rename">Palitan ang pangalan ng %1$s</string>
<string name="action_clear_playlist_filter">I-clear ang filter ng playlist</string>
<string name="action_manage_station_playlists">Idagdag sa playlist</string>
<string name="action_remove_from_playlist">Alisin sa \"%1$s\"</string>
<string name="action_scroll_to_bottom">Mag-scroll pababa</string>
<string name="favorite_list_add_stations">Magdagdag ng mga paborito</string>
<string name="favorite_list_all">Lahat</string>
<string name="favorite_list_assign_title">Idagdag sa mga playlist</string>
<string name="favorite_list_create">Bagong playlist</string>
<string name="favorite_list_delete_message">I-delete ang \"%1$s\"? Mananatili sa Mga Paborito ang iyong mga istasyon.</string>
<string name="favorite_list_delete_title">I-delete ang playlist</string>
<string name="favorite_list_edit_stations">I-edit ang mga istasyon</string>
<string name="favorite_list_empty">Walang laman ang playlist na ito</string>
<string name="favorite_list_manage">Pamahalaan ang mga playlist</string>
<string name="favorite_list_name_hint">Pangalan ng playlist</string>
<string name="favorite_list_none_yet">Wala pang mga playlist. Gumawa ng isa upang ayusin ang iyong mga paborito.</string>
<string name="favorite_list_rename">Palitan ang pangalan ng playlist</string>

[values-fr]
<string name="accessibility_filter_playlist">Filtre de playlist : %1$s</string>
<string name="accessibility_playlist_delete">Supprimer %1$s</string>
<string name="accessibility_playlist_edit_stations">Modifier les stations dans %1$s</string>
<string name="accessibility_playlist_rename">Renommer %1$s</string>
<string name="action_clear_playlist_filter">Effacer le filtre de playlist</string>
<string name="action_manage_station_playlists">Ajouter à la playlist</string>
<string name="action_remove_from_playlist">Retirer de « %1$s »</string>
<string name="action_scroll_to_bottom">Faire défiler vers le bas</string>
<string name="favorite_list_add_stations">Ajouter des favoris</string>
<string name="favorite_list_all">Toutes</string>
<string name="favorite_list_assign_title">Ajouter aux playlists</string>
<string name="favorite_list_create">Nouvelle playlist</string>
<string name="favorite_list_delete_message">Supprimer « %1$s » ? Vos stations resteront dans les Favoris.</string>
<string name="favorite_list_delete_title">Supprimer la playlist</string>
<string name="favorite_list_edit_stations">Modifier les stations</string>
<string name="favorite_list_empty">Cette playlist est vide</string>
<string name="favorite_list_manage">Gérer les playlists</string>
<string name="favorite_list_name_hint">Nom de la playlist</string>
<string name="favorite_list_none_yet">Aucune playlist pour le moment. Créez-en une pour organiser vos favoris.</string>
<string name="favorite_list_rename">Renommer la playlist</string>

[values-ga]
<string name="accessibility_filter_playlist">Scagaire seinmliosta: %1$s</string>
<string name="accessibility_playlist_delete">Scrios %1$s</string>
<string name="accessibility_playlist_edit_stations">Cuir stáisiúin in eagar in %1$s</string>
<string name="accessibility_playlist_rename">Athainmnigh %1$s</string>
<string name="action_clear_playlist_filter">Glan scagaire seinmliosta</string>
<string name="action_manage_station_playlists">Cuir leis an seinmliosta</string>
<string name="action_remove_from_playlist">Bain ó \"%1$s\"</string>
<string name="action_scroll_to_bottom">Scrollaigh go bun</string>
<string name="favorite_list_add_stations">Cuir ceanáin leis</string>
<string name="favorite_list_all">Gach ceann</string>
<string name="favorite_list_assign_title">Cuir le seinmliostaí</string>
<string name="favorite_list_create">Seinmliosta nua</string>
<string name="favorite_list_delete_message">Scrios \"%1$s\"? Fanfaidh do stáisiúin i gCeanáin.</string>
<string name="favorite_list_delete_title">Scrios seinmliosta</string>
<string name="favorite_list_edit_stations">Cuir stáisiúin in eagar</string>
<string name="favorite_list_empty">Tá an seinmliosta seo folamh</string>
<string name="favorite_list_manage">Bainistigh seinmliostaí</string>
<string name="favorite_list_name_hint">Ainm an tseinmliosta</string>
<string name="favorite_list_none_yet">Níl aon seinmliostaí ann fós. Cruthaigh ceann chun do cheanáin a eagrú.</string>
<string name="favorite_list_rename">Athainmnigh seinmliosta</string>

[values-gl]
<string name="accessibility_filter_playlist">Filtro de lista de reprodución: %1$s</string>
<string name="accessibility_playlist_delete">Eliminar %1$s</string>
<string name="accessibility_playlist_edit_stations">Editar emisoras en %1$s</string>
<string name="accessibility_playlist_rename">Cambiar o nome de %1$s</string>
<string name="action_clear_playlist_filter">Borrar filtro de lista de reprodución</string>
<string name="action_manage_station_playlists">Engadir á lista de reprodución</string>
<string name="action_remove_from_playlist">Quitar de \"%1$s\"</string>
<string name="action_scroll_to_bottom">Desprazar cara abaixo</string>
<string name="favorite_list_add_stations">Engadir favoritos</string>
<string name="favorite_list_all">Todas</string>
<string name="favorite_list_assign_title">Engadir ás listas de reprodución</string>
<string name="favorite_list_create">Nova lista de reprodución</string>
<string name="favorite_list_delete_message">Eliminar \"%1$s\"? As túas emisoras permanecerán en Favoritos.</string>
<string name="favorite_list_delete_title">Eliminar lista de reprodución</string>
<string name="favorite_list_edit_stations">Editar emisoras</string>
<string name="favorite_list_empty">Esta lista de reprodución está baleira</string>
<string name="favorite_list_manage">Xestionar listas de reprodución</string>
<string name="favorite_list_name_hint">Nome da lista de reprodución</string>
<string name="favorite_list_none_yet">Aínda non hai listas de reprodución. Crea unha para organizar os teus favoritos.</string>
<string name="favorite_list_rename">Cambiar o nome da lista de reprodución</string>

[values-gu]
<string name="accessibility_filter_playlist">પ્લેલિસ્ટ ફિલ્ટર: %1$s</string>
<string name="accessibility_playlist_delete">%1$s કાઢી નાખો</string>
<string name="accessibility_playlist_edit_stations">%1$s માં સ્ટેશનો સંપાદિત કરો</string>
<string name="accessibility_playlist_rename">%1$s નું નામ બદલો</string>
<string name="action_clear_playlist_filter">પ્લેલિસ્ટ ફિલ્ટર સાફ કરો</string>
<string name="action_manage_station_playlists">પ્લેલિસ્ટમાં ઉમેરો</string>
<string name="action_remove_from_playlist">\"%1$s\" માંથી દૂર કરો</string>
<string name="action_scroll_to_bottom">સૌથી નીચે સ્ક્રોલ કરો</string>
<string name="favorite_list_add_stations">મનપસંદ ઉમેરો</string>
<string name="favorite_list_all">બધા</string>
<string name="favorite_list_assign_title">પ્લેલિસ્ટમાં ઉમેરો</string>
<string name="favorite_list_create">નવી પ્લેલિસ્ટ</string>
<string name="favorite_list_delete_message">\"%1$s\" કાઢી નાખવું છે? તમારા સ્ટેશનો મનપસંદમાં રહેશે.</string>
<string name="favorite_list_delete_title">પ્લેલિસ્ટ કાઢી નાખો</string>
<string name="favorite_list_edit_stations">સ્ટેશનો સંપાદિત કરો</string>
<string name="favorite_list_empty">આ પ્લેલિસ્ટ ખાલી છે</string>
<string name="favorite_list_manage">પ્લેલિસ્ટ મેનેજ કરો</string>
<string name="favorite_list_name_hint">પ્લેલિસ્ટનું નામ</string>
<string name="favorite_list_none_yet">હજુ સુધી કોઈ પ્લેલિસ્ટ નથી. તમારા મનપસંદ સ્ટેશનો ગોઠવવા માટે એક બનાવો.</string>
<string name="favorite_list_rename">પ્લેલિસ્ટનું નામ બદલો</string>

[values-ha]
<string name="accessibility_filter_playlist">Tace jerin waƙoƙi: %1$s</string>
<string name="accessibility_playlist_delete">Share %1$s</string>
<string name="accessibility_playlist_edit_stations">Gyara tashoshi a %1$s</string>
<string name="accessibility_playlist_rename">Sake sunan %1$s</string>
<string name="action_clear_playlist_filter">Share tace jerin waƙoƙi</string>
<string name="action_manage_station_playlists">Ƙara zuwa jerin waƙoƙi</string>
<string name="action_remove_from_playlist">Cire daga \"%1$s\"</string>
<string name="action_scroll_to_bottom">Gungura zuwa ƙasa</string>
<string name="favorite_list_add_stations">Ƙara waɗanda aka fi so</string>
<string name="favorite_list_all">Duka</string>
<string name="favorite_list_assign_title">Ƙara zuwa jerin waƙoƙi</string>
<string name="favorite_list_create">Sabon jerin waƙoƙi</string>
<string name="favorite_list_delete_message">A share \"%1$s\"? Tashoshinka za su ci gaba da kasancewa a cikin Waɗanda aka fi so.</string>
<string name="favorite_list_delete_title">Share jerin waƙoƙi</string>
<string name="favorite_list_edit_stations">Gyara tashoshi</string>
<string name="favorite_list_empty">Wannan jerin waƙoƙin babu komai a ciki</string>
<string name="favorite_list_manage">Sarrafa jerin waƙoƙi</string>
<string name="favorite_list_name_hint">Sunan jerin waƙoƙi</string>
<string name="favorite_list_none_yet">Babu jerin waƙoƙi tukuna. Ƙirƙiri guda ɗaya don tsara waɗanda kuka fi so.</string>
<string name="favorite_list_rename">Sake sunan jerin waƙoƙi</string>

[values-he]
<string name="accessibility_filter_playlist">מסנן רשימות השמעה: %1$s</string>
<string name="accessibility_playlist_delete">מחק את %1$s</string>
<string name="accessibility_playlist_edit_stations">ערוך תחנות ב-%1$s</string>
<string name="accessibility_playlist_rename">שנה את השם של %1$s</string>
<string name="action_clear_playlist_filter">נקה מסנן רשימות השמעה</string>
<string name="action_manage_station_playlists">הוסף לרשימת השמעה</string>
<string name="action_remove_from_playlist">הסר מ-\"%1$s\"</string>
<string name="action_scroll_to_bottom">גלול לתחתית הרשימה</string>
<string name="favorite_list_add_stations">הוסף מועדפים</string>
<string name="favorite_list_all">הכל</string>
<string name="favorite_list_assign_title">הוסף לרשימות השמעה</string>
<string name="favorite_list_create">רשימת השמעה חדשה</string>
<string name="favorite_list_delete_message">למחוק את \"%1$s\"? התחנות שלך יישארו במועדפים.</string>
<string name="favorite_list_delete_title">מחק רשימת השמעה</string>
<string name="favorite_list_edit_stations">ערוך תחנות</string>
<string name="favorite_list_empty">רשימת השמעה זו ריקה</string>
<string name="favorite_list_manage">נהל רשימות השמעה</string>
<string name="favorite_list_name_hint">שם רשימת ההשמעה</string>
<string name="favorite_list_none_yet">אין עדיין רשימות השמעה. צור רשימה כדי לארגן את המועדפים שלך.</string>
<string name="favorite_list_rename">שנה שם רשימת השמעה</string>

[values-hi]
<string name="accessibility_filter_playlist">प्लेलिस्ट फ़िल्टर: %1$s</string>
<string name="accessibility_playlist_delete">%1$s हटाएं</string>
<string name="accessibility_playlist_edit_stations">%1$s में स्टेशन संपादित करें</string>
<string name="accessibility_playlist_rename">%1$s का नाम बदलें</string>
<string name="action_clear_playlist_filter">प्लेलिस्ट फ़िल्टर साफ़ करें</string>
<string name="action_manage_station_playlists">प्लेलिस्ट में जोड़ें</string>
<string name="action_remove_from_playlist">\"%1$s\" से हटाएं</string>
<string name="action_scroll_to_bottom">सबसे नीचे स्क्रॉल करें</string>
<string name="favorite_list_add_stations">पसंदीदा जोड़ें</string>
<string name="favorite_list_all">सभी</string>
<string name="favorite_list_assign_title">प्लेलिस्ट में जोड़ें</string>
<string name="favorite_list_create">नई प्लेलिस्ट</string>
<string name="favorite_list_delete_message">\"%1$s\" हटाएं? आपके स्टेशन पसंदीदा में बने रहेंगे।</string>
<string name="favorite_list_delete_title">प्लेलिस्ट हटाएं</string>
<string name="favorite_list_edit_stations">स्टेशन संपादित करें</string>
<string name="favorite_list_empty">यह प्लेलिस्ट खाली है</string>
<string name="favorite_list_manage">प्लेलिस्ट प्रबंधित करें</string>
<string name="favorite_list_name_hint">प्लेलिस्ट का नाम</string>
<string name="favorite_list_none_yet">अभी तक कोई प्लेलिस्ट नहीं है। अपने पसंदीदा स्टेशन व्यवस्थित करने के लिए एक बनाएं।</string>
<string name="favorite_list_rename">प्लेलिस्ट का नाम बदलें</string>

[values-hr]
<string name="accessibility_filter_playlist">Filtar popisa za reprodukciju: %1$s</string>
<string name="accessibility_playlist_delete">Izbriši %1$s</string>
<string name="accessibility_playlist_edit_stations">Uredi stanice u %1$s</string>
<string name="accessibility_playlist_rename">Preimenuj %1$s</string>
<string name="action_clear_playlist_filter">Očisti filtar popisa za reprodukciju</string>
<string name="action_manage_station_playlists">Dodaj na popis za reprodukciju</string>
<string name="action_remove_from_playlist">Ukloni s popisa \"%1$s\"</string>
<string name="action_scroll_to_bottom">Pomakni na dno</string>
<string name="favorite_list_add_stations">Dodaj favorite</string>
<string name="favorite_list_all">Sve</string>
<string name="favorite_list_assign_title">Dodaj na popise za reprodukciju</string>
<string name="favorite_list_create">Novi popis za reprodukciju</string>
<string name="favorite_list_delete_message">Izbrisati \"%1$s\"? Vaše će stanice ostati u Favoritima.</string>
<string name="favorite_list_delete_title">Izbriši popis za reprodukciju</string>
<string name="favorite_list_edit_stations">Uredi stanice</string>
<string name="favorite_list_empty">Ovaj popis za reprodukciju je prazan</string>
<string name="favorite_list_manage">Upravljaj popisima za reprodukciju</string>
<string name="favorite_list_name_hint">Naziv popisa za reprodukciju</string>
<string name="favorite_list_none_yet">Još nema popisa za reprodukciju. Izradite jedan kako biste organizirali favorite.</string>
<string name="favorite_list_rename">Preimenuj popis za reprodukciju</string>

[values-hu]
<string name="accessibility_filter_playlist">Lejátszási lista szűrő: %1$s</string>
<string name="accessibility_playlist_delete">%1$s törlése</string>
<string name="accessibility_playlist_edit_stations">Állomások szerkesztése itt: %1$s</string>
<string name="accessibility_playlist_rename">%1$s átnevezése</string>
<string name="action_clear_playlist_filter">Lejátszási lista szűrő törlése</string>
<string name="action_manage_station_playlists">Hozzáadás lejátszási listához</string>
<string name="action_remove_from_playlist">Eltávolítás innen: \"%1$s\"</string>
<string name="action_scroll_to_bottom">Görgetés az aljára</string>
<string name="favorite_list_add_stations">Kedvencek hozzáadása</string>
<string name="favorite_list_all">Összes</string>
<string name="favorite_list_assign_title">Hozzáadás lejátszási listákhoz</string>
<string name="favorite_list_create">Új lejátszási lista</string>
<string name="favorite_list_delete_message">Törli a(z) \"%1$s\" listát? Az állomásai a Kedvencek között maradnak.</string>
<string name="favorite_list_delete_title">Lejátszási lista törlése</string>
<string name="favorite_list_edit_stations">Állomások szerkesztése</string>
<string name="favorite_list_empty">Ez a lejátszási lista üres</string>
<string name="favorite_list_manage">Lejátszási listák kezelése</string>
<string name="favorite_list_name_hint">Lejátszási lista neve</string>
<string name="favorite_list_none_yet">Még nincsenek lejátszási listák. Hozzon létre egyet a kedvencek rendszerezéséhez.</string>
<string name="favorite_list_rename">Lejátszási lista átnevezése</string>

[values-hy]
<string name="accessibility_filter_playlist">Երգացանկի զտիչ՝ %1$s</string>
<string name="accessibility_playlist_delete">Ջնջել %1$s-ը</string>
<string name="accessibility_playlist_edit_stations">Խմբագրել կայանները %1$s-ում</string>
<string name="accessibility_playlist_rename">Վերանվանել %1$s-ը</string>
<string name="action_clear_playlist_filter">Մաքրել երգացանկի զտիչը</string>
<string name="action_manage_station_playlists">Ավելացնել երգացանկում</string>
<string name="action_remove_from_playlist">Հեռացնել «%1$s»-ից</string>
<string name="action_scroll_to_bottom">Ոլորել ներքև</string>
<string name="favorite_list_add_stations">Ավելացնել ընտրյալներ</string>
<string name="favorite_list_all">Բոլորը</string>
<string name="favorite_list_assign_title">Ավելացնել երգացանկերում</string>
<string name="favorite_list_create">Նոր երգացանկ</string>
<string name="favorite_list_delete_message">Ջնջե՞լ «%1$s»-ը: Ձեր կայանները կմնան Ընտրյալներում:</string>
<string name="favorite_list_delete_title">Ջնջել երգացանկը</string>
<string name="favorite_list_edit_stations">Խմբագրել կայանները</string>
<string name="favorite_list_empty">Այս երգացանկը դատարկ է</string>
<string name="favorite_list_manage">Կառավարել երգացանկերը</string>
<string name="favorite_list_name_hint">Երգացանկի անվանումը</string>
<string name="favorite_list_none_yet">Դեռևս երգացանկեր չկան: Ստեղծեք մեկը՝ ձեր ընտրյալները կազմակերպելու համար:</string>
<string name="favorite_list_rename">Վերանվանել երգացանկը</string>

[values-id]
<string name="accessibility_filter_playlist">Filter daftar putar: %1$s</string>
<string name="accessibility_playlist_delete">Hapus %1$s</string>
<string name="accessibility_playlist_edit_stations">Edit stasiun di %1$s</string>
<string name="accessibility_playlist_rename">Ganti nama %1$s</string>
<string name="action_clear_playlist_filter">Hapus filter daftar putar</string>
<string name="action_manage_station_playlists">Tambahkan ke daftar putar</string>
<string name="action_remove_from_playlist">Hapus dari \"%1$s\"</string>
<string name="action_scroll_to_bottom">Gulir ke paling bawah</string>
<string name="favorite_list_add_stations">Tambahkan favorit</string>
<string name="favorite_list_all">Semua</string>
<string name="favorite_list_assign_title">Tambahkan ke daftar putar</string>
<string name="favorite_list_create">Daftar putar baru</string>
<string name="favorite_list_delete_message">Hapus \"%1$s\"? Stasiun Anda akan tetap tersimpan di Favorit.</string>
<string name="favorite_list_delete_title">Hapus daftar putar</string>
<string name="favorite_list_edit_stations">Edit stasiun</string>
<string name="favorite_list_empty">Daftar putar ini kosong</string>
<string name="favorite_list_manage">Kelola daftar putar</string>
<string name="favorite_list_name_hint">Nama daftar putar</string>
<string name="favorite_list_none_yet">Belum ada daftar putar. Buat satu untuk mengatur favorit Anda.</string>
<string name="favorite_list_rename">Ganti nama daftar putar</string>

[values-ig]
<string name="accessibility_filter_playlist">Nzacha ndepụta ọkpụkpọ: %1$s</string>
<string name="accessibility_playlist_delete">Hichapụ %1$s</string>
<string name="accessibility_playlist_edit_stations">Dezie ọdụ na %1$s</string>
<string name="accessibility_playlist_rename">Gbanwee aha %1$s</string>
<string name="action_clear_playlist_filter">Hichapụ nzacha ndepụta ọkpụkpọ</string>
<string name="action_manage_station_playlists">Tinye na ndepụta ọkpụkpọ</string>
<string name="action_remove_from_playlist">Wepụ na \"%1$s\"</string>
<string name="action_scroll_to_bottom">Pịgharịa gaa n\'ala</string>
<string name="favorite_list_add_stations">Tinye ọkacha mmasị</string>
<string name="favorite_list_all">Niile</string>
<string name="favorite_list_assign_title">Tinye na ndepụta ọkpụkpọ</string>
<string name="favorite_list_create">Ndepụta ọkpụkpọ ọhụrụ</string>
<string name="favorite_list_delete_message">Hichapụ \"%1$s\"? Ọdụ gị ga-anọgide na Ọkacha mmasị.</string>
<string name="favorite_list_delete_title">Hichapụ ndepụta ọkpụkpọ</string>
<string name="favorite_list_edit_stations">Dezie ọdụ</string>
<string name="favorite_list_empty">Ndepụta ọkpụkpọ a tọgbọ chakoo</string>
<string name="favorite_list_manage">Jikwaa ndepụta ọkpụkpọ</string>
<string name="favorite_list_name_hint">Aha ndepụta ọkpụkpọ</string>
<string name="favorite_list_none_yet">Enwebeghị ndepụta ọkpụkpọ. Mepụta otu iji hazie ọkacha mmasị gị.</string>
<string name="favorite_list_rename">Gbanwee aha ndepụta ọkpụkpọ</string>

[values-in]
<string name="accessibility_filter_playlist">Filter daftar putar: %1$s</string>
<string name="accessibility_playlist_delete">Hapus %1$s</string>
<string name="accessibility_playlist_edit_stations">Edit stasiun di %1$s</string>
<string name="accessibility_playlist_rename">Ganti nama %1$s</string>
<string name="action_clear_playlist_filter">Hapus filter daftar putar</string>
<string name="action_manage_station_playlists">Tambahkan ke daftar putar</string>
<string name="action_remove_from_playlist">Hapus dari \"%1$s\"</string>
<string name="action_scroll_to_bottom">Gulir ke paling bawah</string>
<string name="favorite_list_add_stations">Tambahkan favorit</string>
<string name="favorite_list_all">Semua</string>
<string name="favorite_list_assign_title">Tambahkan ke daftar putar</string>
<string name="favorite_list_create">Daftar putar baru</string>
<string name="favorite_list_delete_message">Hapus \"%1$s\"? Stasiun Anda akan tetap tersimpan di Favorit.</string>
<string name="favorite_list_delete_title">Hapus daftar putar</string>
<string name="favorite_list_edit_stations">Edit stasiun</string>
<string name="favorite_list_empty">Daftar putar ini kosong</string>
<string name="favorite_list_manage">Kelola daftar putar</string>
<string name="favorite_list_name_hint">Nama daftar putar</string>
<string name="favorite_list_none_yet">Belum ada daftar putar. Buat satu untuk mengatur favorit Anda.</string>
<string name="favorite_list_rename">Ganti nama daftar putar</string>

[values-is]
<string name="accessibility_filter_playlist">Spilunarlistasía: %1$s</string>
<string name="accessibility_playlist_delete">Eyða %1$s</string>
<string name="accessibility_playlist_edit_stations">Breyta stöðvum í %1$s</string>
<string name="accessibility_playlist_rename">Endurnefna %1$s</string>
<string name="action_clear_playlist_filter">Hreinsa spilunarlistasíu</string>
<string name="action_manage_station_playlists">Bæta á spilunarlista</string>
<string name="action_remove_from_playlist">Fjarlægja af \"%1$s\"</string>
<string name="action_scroll_to_bottom">Skruna neðst</string>
<string name="favorite_list_add_stations">Bæta við uppáhalds</string>
<string name="favorite_list_all">Allar</string>
<string name="favorite_list_assign_title">Bæta á spilunarlista</string>
<string name="favorite_list_create">Nýr spilunarlisti</string>
<string name="favorite_list_delete_message">Eyða „%1$s“? Stöðvarnar þínar verða áfram í Uppáhalds.</string>
<string name="favorite_list_delete_title">Eyða spilunarlista</string>
<string name="favorite_list_edit_stations">Breyta stöðvum</string>
<string name="favorite_list_empty">Þessi spilunarlisti er tómur</string>
<string name="favorite_list_manage">Stjórna spilunarlistum</string>
<string name="favorite_list_name_hint">Heiti spilunarlista</string>
<string name="favorite_list_none_yet">Engir spilunarlistar ennþá. Búðu til einn til að skipuleggja uppáhaldsstöðvarnar þínar.</string>
<string name="favorite_list_rename">Endurnefna spilunarlista</string>

[values-it]
<string name="accessibility_filter_playlist">Filtro playlist: %1$s</string>
<string name="accessibility_playlist_delete">Elimina %1$s</string>
<string name="accessibility_playlist_edit_stations">Modifica stazioni in %1$s</string>
<string name="accessibility_playlist_rename">Rinomina %1$s</string>
<string name="action_clear_playlist_filter">Cancella filtro playlist</string>
<string name="action_manage_station_playlists">Aggiungi alla playlist</string>
<string name="action_remove_from_playlist">Rimuovi da \"%1$s\"</string>
<string name="action_scroll_to_bottom">Scorri fino in fondo</string>
<string name="favorite_list_add_stations">Aggiungi preferiti</string>
<string name="favorite_list_all">Tutte</string>
<string name="favorite_list_assign_title">Aggiungi alle playlist</string>
<string name="favorite_list_create">Nuova playlist</string>
<string name="favorite_list_delete_message">Eliminare \"%1$s\"? Le tue stazioni rimarranno nei Preferiti.</string>
<string name="favorite_list_delete_title">Elimina playlist</string>
<string name="favorite_list_edit_stations">Modifica stazioni</string>
<string name="favorite_list_empty">Questa playlist è vuota</string>
<string name="favorite_list_manage">Gestisci playlist</string>
<string name="favorite_list_name_hint">Nome della playlist</string>
<string name="favorite_list_none_yet">Nessuna playlist ancora. Creane una per organizzare i tuoi preferiti.</string>
<string name="favorite_list_rename">Rinomina playlist</string>

[values-iw]
<string name="accessibility_filter_playlist">מסנן רשימות השמעה: %1$s</string>
<string name="accessibility_playlist_delete">מחק את %1$s</string>
<string name="accessibility_playlist_edit_stations">ערוך תחנות ב-%1$s</string>
<string name="accessibility_playlist_rename">שנה את השם של %1$s</string>
<string name="action_clear_playlist_filter">נקה מסנן רשימות השמעה</string>
<string name="action_manage_station_playlists">הוסף לרשימת השמעה</string>
<string name="action_remove_from_playlist">הסר מ-\"%1$s\"</string>
<string name="action_scroll_to_bottom">גלול לתחתית הרשימה</string>
<string name="favorite_list_add_stations">הוסף מועדפים</string>
<string name="favorite_list_all">הכל</string>
<string name="favorite_list_assign_title">הוסף לרשימות השמעה</string>
<string name="favorite_list_create">רשימת השמעה חדשה</string>
<string name="favorite_list_delete_message">למחוק את \"%1$s\"? התחנות שלך יישארו במועדפים.</string>
<string name="favorite_list_delete_title">מחק רשימת השמעה</string>
<string name="favorite_list_edit_stations">ערוך תחנות</string>
<string name="favorite_list_empty">רשימת השמעה זו ריקה</string>
<string name="favorite_list_manage">נהל רשימות השמעה</string>
<string name="favorite_list_name_hint">שם רשימת ההשמעה</string>
<string name="favorite_list_none_yet">אין עדיין רשימות השמעה. צור רשימה כדי לארגן את המועדפים שלך.</string>
<string name="favorite_list_rename">שנה שם רשימת השמעה</string>

[values-ja]
<string name="accessibility_filter_playlist">プレイリストフィルター: %1$s</string>
<string name="accessibility_playlist_delete">%1$s を削除</string>
<string name="accessibility_playlist_edit_stations">%1$s の放送局を編集</string>
<string name="accessibility_playlist_rename">%1$s の名前を変更</string>
<string name="action_clear_playlist_filter">プレイリストフィルターをクリア</string>
<string name="action_manage_station_playlists">プレイリストに追加</string>
<string name="action_remove_from_playlist">「%1$s」から削除</string>
<string name="action_scroll_to_bottom">一番下へスクロール</string>
<string name="favorite_list_add_stations">お気に入りを追加</string>
<string name="favorite_list_all">すべて</string>
<string name="favorite_list_assign_title">プレイリストに追加</string>
<string name="favorite_list_create">新しいプレイリスト</string>
<string name="favorite_list_delete_message">「%1$s」を削除しますか？放送局はお気に入りに残ります。</string>
<string name="favorite_list_delete_title">プレイリストを削除</string>
<string name="favorite_list_edit_stations">放送局を編集</string>
<string name="favorite_list_empty">このプレイリストは空です</string>
<string name="favorite_list_manage">プレイリストを管理</string>
<string name="favorite_list_name_hint">プレイリスト名</string>
<string name="favorite_list_none_yet">プレイリストはまだありません。お気に入りを整理するために作成しましょう。</string>
<string name="favorite_list_rename">プレイリスト名を変更</string>

[values-ji]
<string name="accessibility_filter_playlist">פּלייליסט פֿילטער: %1$s</string>
<string name="accessibility_playlist_delete">אויסמעקן %1$s</string>
<string name="accessibility_playlist_edit_stations">רעדאַגירן סטאַנציעס אין %1$s</string>
<string name="accessibility_playlist_rename">ענדערן נאָמען פֿון %1$s</string>
<string name="action_clear_playlist_filter">אויסמעקן פּלייליסט פֿילטער</string>
<string name="action_manage_station_playlists">צוגעבן צו פּלייליסט</string>
<string name="action_remove_from_playlist">אַראָפּנעמען פֿון \"%1$s\"</string>
<string name="action_scroll_to_bottom">סקראָלן צום דנאָ</string>
<string name="favorite_list_add_stations">צוגעבן פֿאַוואָריטן</string>
<string name="favorite_list_all">אַלע</string>
<string name="favorite_list_assign_title">צוגעבן צו פּלייליסטן</string>
<string name="favorite_list_create">נײַע פּלייליסט</string>
<string name="favorite_list_delete_message">אויסמעקן \"%1$s\"? אײַערע סטאַנציעס וועלן בלײַבן אין פֿאַוואָריטן.</string>
<string name="favorite_list_delete_title">אויסמעקן פּלייליסט</string>
<string name="favorite_list_edit_stations">רעדאַגירן סטאַנציעס</string>
<string name="favorite_list_empty">די פּלייליסט איז ליידיק</string>
<string name="favorite_list_manage">פֿאַרוואַלטן פּלייליסטן</string>
<string name="favorite_list_name_hint">פּלייליסט נאָמען</string>
<string name="favorite_list_none_yet">נאָך נישטאָ קיין פּלייליסטן. שאַפֿט איינע צו אָרגאַניזירן אײַערע פֿאַוואָריטן.</string>
<string name="favorite_list_rename">ענדערן פּלייליסט נאָמען</string>

[values-ka]
<string name="accessibility_filter_playlist">დასაკრავი სიის ფილტრი: %1$s</string>
<string name="accessibility_playlist_delete">%1$s-ის წაშლა</string>
<string name="accessibility_playlist_edit_stations">სადგურების რედაქტირება სიაში %1$s</string>
<string name="accessibility_playlist_rename">%1$s-ის გადარქმევა</string>
<string name="action_clear_playlist_filter">დასაკრავი სიის ფილტრის გასუფთავება</string>
<string name="action_manage_station_playlists">დასაკრავ სიაში დამატება</string>
<string name="action_remove_from_playlist">წაშლა სიიდან \"%1$s\"</string>
<string name="action_scroll_to_bottom">ბოლოში ჩასვლა</string>
<string name="favorite_list_add_stations">რჩეულების დამატება</string>
<string name="favorite_list_all">ყველა</string>
<string name="favorite_list_assign_title">დასაკრავ სიებში დამატება</string>
<string name="favorite_list_create">ახალი დასაკრავი სია</string>
<string name="favorite_list_delete_message">წაიშალოს \"%1$s\"? თქვენი სადგურები რჩეულებში დარჩება.</string>
<string name="favorite_list_delete_title">დასაკრავი სიის წაშლა</string>
<string name="favorite_list_edit_stations">სადგურების რედაქტირება</string>
<string name="favorite_list_empty">ეს დასაკრავი სია ცარიელია</string>
<string name="favorite_list_manage">დასაკრავი სიების მართვა</string>
<string name="favorite_list_name_hint">დასაკრავი სიის სახელი</string>
<string name="favorite_list_none_yet">დასაკრავი სიები ჯერ არ არის. შექმენით ერთი თქვენი რჩეულების მოსაწესრიგებლად.</string>
<string name="favorite_list_rename">დასაკრავი სიის გადარქმევა</string>

[values-kk]
<string name="accessibility_filter_playlist">Ойнату тізімінің сүзгісі: %1$s</string>
<string name="accessibility_playlist_delete">%1$s жою</string>
<string name="accessibility_playlist_edit_stations">%1$s ішіндегі станцияларды өңдеу</string>
<string name="accessibility_playlist_rename">%1$s атауын өзгерту</string>
<string name="action_clear_playlist_filter">Ойнату тізімінің сүзгісін тазалау</string>
<string name="action_manage_station_playlists">Ойнату тізіміне қосу</string>
<string name="action_remove_from_playlist">\"%1$s\" ішінен өшіру</string>
<string name="action_scroll_to_bottom">Төменге айналдыру</string>
<string name="favorite_list_add_stations">Таңдаулыларды қосу</string>
<string name="favorite_list_all">Барлығы</string>
<string name="favorite_list_assign_title">Ойнату тізімдеріне қосу</string>
<string name="favorite_list_create">Жаңа ойнату тізімі</string>
<string name="favorite_list_delete_message">\"%1$s\" жойылсын ба? Станцияларыңыз Таңдаулыларда қалады.</string>
<string name="favorite_list_delete_title">Ойнату тізімін жою</string>
<string name="favorite_list_edit_stations">Станцияларды өңдеу</string>
<string name="favorite_list_empty">Бұл ойнату тізімі бос</string>
<string name="favorite_list_manage">Ойнату тізімдерін басқару</string>
<string name="favorite_list_name_hint">Ойнату тізімінің атауы</string>
<string name="favorite_list_none_yet">Әзірге ойнату тізімдері жоқ. Таңдаулыларды реттеу үшін біреуін жасаңыз.</string>
<string name="favorite_list_rename">Ойнату тізімінің атауын өзгерту</string>

[values-km]
<string name="accessibility_filter_playlist">តម្រងបញ្ជីចាក់៖ %1$s</string>
<string name="accessibility_playlist_delete">លុប %1$s</string>
<string name="accessibility_playlist_edit_stations">កែសម្រួលស្ថានីយក្នុង %1$s</string>
<string name="accessibility_playlist_rename">ប្តូរឈ្មោះ %1$s</string>
<string name="action_clear_playlist_filter">សម្អាតតម្រងបញ្ជីចាក់</string>
<string name="action_manage_station_playlists">បន្ថែមទៅបញ្ជីចាក់</string>
<string name="action_remove_from_playlist">ដកចេញពី \"%1$s\"</string>
<string name="action_scroll_to_bottom">រំកិលទៅបាត</string>
<string name="favorite_list_add_stations">បន្ថែមចំណូលចិត្ត</string>
<string name="favorite_list_all">ទាំងអស់</string>
<string name="favorite_list_assign_title">បន្ថែមទៅបញ្ជីចាក់</string>
<string name="favorite_list_create">បញ្ជីចាក់ថ្មី</string>
<string name="favorite_list_delete_message">លុប \"%1$s\" ឬ? ស្ថានីយរបស់អ្នកនឹងនៅតែមានក្នុងចំណូលចិត្ត។</string>
<string name="favorite_list_delete_title">លុបបញ្ជីចាក់</string>
<string name="favorite_list_edit_stations">កែសម្រួលស្ថានីយ</string>
<string name="favorite_list_empty">បញ្ជីចាក់នេះទទេ</string>
<string name="favorite_list_manage">គ្រប់គ្រងបញ្ជីចាក់</string>
<string name="favorite_list_name_hint">ឈ្មោះបញ្ជីចាក់</string>
<string name="favorite_list_none_yet">មិនទាន់មានបញ្ជីចាក់នៅឡើយទេ។ បង្កើតមួយដើម្បីរៀបចំចំណូលចិត្តរបស់អ្នក។</string>
<string name="favorite_list_rename">ប្តូរឈ្មោះបញ្ជីចាក់</string>

[values-kn]
<string name="accessibility_filter_playlist">ಪ್ಲೇಪಟ್ಟಿ ಫಿಲ್ಟರ್: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ಅಳಿಸಿ</string>
<string name="accessibility_playlist_edit_stations">%1$s ನಲ್ಲಿ ನಿಲ್ದಾಣಗಳನ್ನು ಸಂಪಾದಿಸಿ</string>
<string name="accessibility_playlist_rename">%1$s ಮರುಹೆಸರಿಸಿ</string>
<string name="action_clear_playlist_filter">ಪ್ಲೇಪಟ್ಟಿ ಫಿಲ್ಟರ್ ತೆರವುಗೊಳಿಸಿ</string>
<string name="action_manage_station_playlists">ಪ್ಲೇಪಟ್ಟಿಗೆ ಸೇರಿಸಿ</string>
<string name="action_remove_from_playlist">\"%1$s\" ನಿಂದ ತೆಗೆದುಹಾಕಿ</string>
<string name="action_scroll_to_bottom">ಕೆಳಕ್ಕೆ ಸ್ಕ್ರಾಲ್ ಮಾಡಿ</string>
<string name="favorite_list_add_stations">ಮೆಚ್ಚಿನವುಗಳನ್ನು ಸೇರಿಸಿ</string>
<string name="favorite_list_all">ಎಲ್ಲಾ</string>
<string name="favorite_list_assign_title">ಪ್ಲೇಪಟ್ಟಿಗಳಿಗೆ ಸೇರಿಸಿ</string>
<string name="favorite_list_create">ಹೊಸ ಪ್ಲೇಪಟ್ಟಿ</string>
<string name="favorite_list_delete_message">\"%1$s\" ಅಳಿಸುವುದೇ? ನಿಮ್ಮ ನಿಲ್ದಾಣಗಳು ಮೆಚ್ಚಿನವುಗಳಲ್ಲಿ ಉಳಿಯುತ್ತವೆ.</string>
<string name="favorite_list_delete_title">ಪ್ಲೇಪಟ್ಟಿ ಅಳಿಸಿ</string>
<string name="favorite_list_edit_stations">ನಿಲ್ದಾಣಗಳನ್ನು ಸಂಪಾದಿಸಿ</string>
<string name="favorite_list_empty">ಈ ಪ್ಲೇಪಟ್ಟಿ ಖಾಲಿಯಾಗಿದೆ</string>
<string name="favorite_list_manage">ಪ್ಲೇಪಟ್ಟಿಗಳನ್ನು ನಿರ್ವಹಿಸಿ</string>
<string name="favorite_list_name_hint">ಪ್ಲೇಪಟ್ಟಿ ಹೆಸರು</string>
<string name="favorite_list_none_yet">ಇನ್ನೂ ಯಾವುದೇ ಪ್ಲೇಪಟ್ಟಿಗಳಿಲ್ಲ. ನಿಮ್ಮ ಮೆಚ್ಚಿನವುಗಳನ್ನು ಸಂಘಟಿಸಲು ಒಂದನ್ನು ರಚಿಸಿ.</string>
<string name="favorite_list_rename">ಪ್ಲೇಪಟ್ಟಿ ಮರುಹೆಸರಿಸಿ</string>

[values-ko]
<string name="accessibility_filter_playlist">재생목록 필터: %1$s</string>
<string name="accessibility_playlist_delete">%1$s 삭제</string>
<string name="accessibility_playlist_edit_stations">%1$s의 방송국 편집</string>
<string name="accessibility_playlist_rename">%1$s 이름 변경</string>
<string name="action_clear_playlist_filter">재생목록 필터 지우기</string>
<string name="action_manage_station_playlists">재생목록에 추가</string>
<string name="action_remove_from_playlist">\"%1$s\"에서 제거</string>
<string name="action_scroll_to_bottom">맨 아래로 스크롤</string>
<string name="favorite_list_add_stations">즐겨찾기 추가</string>
<string name="favorite_list_all">전체</string>
<string name="favorite_list_assign_title">재생목록에 추가</string>
<string name="favorite_list_create">새 재생목록</string>
<string name="favorite_list_delete_message">\"%1$s\" 재생목록을 삭제할까요? 방송국은 즐겨찾기에 그대로 유지됩니다.</string>
<string name="favorite_list_delete_title">재생목록 삭제</string>
<string name="favorite_list_edit_stations">방송국 편집</string>
<string name="favorite_list_empty">재생목록이 비어 있습니다</string>
<string name="favorite_list_manage">재생목록 관리</string>
<string name="favorite_list_name_hint">재생목록 이름</string>
<string name="favorite_list_none_yet">아직 재생목록이 없습니다. 즐겨찾기를 정리하려면 새 재생목록을 만드세요.</string>
<string name="favorite_list_rename">재생목록 이름 변경</string>

[values-ky]
<string name="accessibility_filter_playlist">Ойнотмо тизме чыпкасы: %1$s</string>
<string name="accessibility_playlist_delete">%1$s өчүрүү</string>
<string name="accessibility_playlist_edit_stations">%1$s ичиндеги станцияларды түзөтүү</string>
<string name="accessibility_playlist_rename">%1$s атын өзгөртүү</string>
<string name="action_clear_playlist_filter">Ойнотмо тизме чыпкасын тазалоо</string>
<string name="action_manage_station_playlists">Ойнотмо тизмеге кошуу</string>
<string name="action_remove_from_playlist">\"%1$s\" тизмесинен алып салуу</string>
<string name="action_scroll_to_bottom">Төмөнгө сыдыруу</string>
<string name="favorite_list_add_stations">Сүйүктүүлөрдү кошуу</string>
<string name="favorite_list_all">Баары</string>
<string name="favorite_list_assign_title">Ойнотмо тизмелерге кошуу</string>
<string name="favorite_list_create">Жаңы ойнотмо тизме</string>
<string name="favorite_list_delete_message">\"%1$s\" өчүрүлсүнбү? Станцияларыңыз Сүйүктүүлөрдө кала берет.</string>
<string name="favorite_list_delete_title">Ойнотмо тизмени өчүрүү</string>
<string name="favorite_list_edit_stations">Станцияларды түзөтүү</string>
<string name="favorite_list_empty">Бул ойнотмо тизме бош</string>
<string name="favorite_list_manage">Ойнотмо тизмелерди башкаруу</string>
<string name="favorite_list_name_hint">Ойнотмо тизменин аталышы</string>
<string name="favorite_list_none_yet">Азырынча ойнотмо тизмелер жок. Сүйүктүүлөрдү иреттөө үчүн бирин түзүңүз.</string>
<string name="favorite_list_rename">Ойнотмо тизменин атын өзгөртүү</string>

[values-lo]
<string name="accessibility_filter_playlist">ຕົວກອງລາຍການຫຼິ້ນ: %1$s</string>
<string name="accessibility_playlist_delete">ລຶບ %1$s</string>
<string name="accessibility_playlist_edit_stations">ແກ້ໄຂສະຖານີໃນ %1$s</string>
<string name="accessibility_playlist_rename">ປ່ຽນຊື່ %1$s</string>
<string name="action_clear_playlist_filter">ລ້າງຕົວກອງລາຍການຫຼິ້ນ</string>
<string name="action_manage_station_playlists">ເພີ່ມໃສ່ລາຍການຫຼິ້ນ</string>
<string name="action_remove_from_playlist">ລຶບອອກຈາກ \"%1$s\"</string>
<string name="action_scroll_to_bottom">ເລື່ອນລົງລຸ່ມສຸດ</string>
<string name="favorite_list_add_stations">ເພີ່ມລາຍການທີ່ມັກ</string>
<string name="favorite_list_all">ທັງໝົດ</string>
<string name="favorite_list_assign_title">ເພີ່ມໃສ່ລາຍການຫຼິ້ນ</string>
<string name="favorite_list_create">ລາຍການຫຼິ້ນໃໝ່</string>
<string name="favorite_list_delete_message">ລຶບ \"%1$s\" ບໍ? ສະຖານີຂອງທ່ານຈະຍັງຢູ່ໃນລາຍການທີ່ມັກ.</string>
<string name="favorite_list_delete_title">ລຶບລາຍການຫຼິ້ນ</string>
<string name="favorite_list_edit_stations">ແກ້ໄຂສະຖານີ</string>
<string name="favorite_list_empty">ລາຍການຫຼິ້ນນີ້ຫວ່າງເປົ່າ</string>
<string name="favorite_list_manage">ຈັດການລາຍການຫຼິ້ນ</string>
<string name="favorite_list_name_hint">ຊື່ລາຍການຫຼິ້ນ</string>
<string name="favorite_list_none_yet">ຍັງບໍ່ມີລາຍການຫຼິ້ນເທື່ອ. ສ້າງອັນໜຶ່ງເພື່ອຈັດລະບຽບລາຍການທີ່ມັກຂອງທ່ານ.</string>
<string name="favorite_list_rename">ປ່ຽນຊື່ລາຍການຫຼິ້ນ</string>

[values-lt]
<string name="accessibility_filter_playlist">Grojaraščio filtras: %1$s</string>
<string name="accessibility_playlist_delete">Ištrinti %1$s</string>
<string name="accessibility_playlist_edit_stations">Redaguoti stotis grojaraštyje %1$s</string>
<string name="accessibility_playlist_rename">Pervardyti %1$s</string>
<string name="action_clear_playlist_filter">Išvalyti grojaraščio filtrą</string>
<string name="action_manage_station_playlists">Pridėti prie grojaraščio</string>
<string name="action_remove_from_playlist">Pašalinti iš \"%1$s\"</string>
<string name="action_scroll_to_bottom">Slinkti į apačią</string>
<string name="favorite_list_add_stations">Pridėti mėgstamiausių</string>
<string name="favorite_list_all">Visos</string>
<string name="favorite_list_assign_title">Pridėti prie grojaraščių</string>
<string name="favorite_list_create">Naujas grojaraštis</string>
<string name="favorite_list_delete_message">Ištrinti \"%1$s\"? Jūsų stotys liks Mėgstamiausiuose.</string>
<string name="favorite_list_delete_title">Ištrinti grojaraštį</string>
<string name="favorite_list_edit_stations">Redaguoti stotis</string>
<string name="favorite_list_empty">Šis grojaraštis tuščias</string>
<string name="favorite_list_manage">Tvarkyti grojaraščius</string>
<string name="favorite_list_name_hint">Grojaraščio pavadinimas</string>
<string name="favorite_list_none_yet">Grojaraščių dar nėra. Sukurkite vieną mėgstamiausioms stotims suskirstyti.</string>
<string name="favorite_list_rename">Pervardyti grojaraštį</string>

[values-lv]
<string name="accessibility_filter_playlist">Atskaņošanas saraksta filtrs: %1$s</string>
<string name="accessibility_playlist_delete">Dzēst %1$s</string>
<string name="accessibility_playlist_edit_stations">Rediģēt stacijas sarakstā %1$s</string>
<string name="accessibility_playlist_rename">Pārdēvēt %1$s</string>
<string name="action_clear_playlist_filter">Notīrīt atskaņošanas saraksta filtru</string>
<string name="action_manage_station_playlists">Pievienot atskaņošanas sarakstam</string>
<string name="action_remove_from_playlist">Noņemt no \"%1$s\"</string>
<string name="action_scroll_to_bottom">Ritināt uz leju</string>
<string name="favorite_list_add_stations">Pievienot izlasei</string>
<string name="favorite_list_all">Visas</string>
<string name="favorite_list_assign_title">Pievienot atskaņošanas sarakstiem</string>
<string name="favorite_list_create">Jauns atskaņošanas saraksts</string>
<string name="favorite_list_delete_message">Vai dzēst \"%1$s\"? Jūsu stacijas paliks sadaļā Izlase.</string>
<string name="favorite_list_delete_title">Dzēst atskaņošanas sarakstu</string>
<string name="favorite_list_edit_stations">Rediģēt stacijas</string>
<string name="favorite_list_empty">Šis atskaņošanas saraksts ir tukšs</string>
<string name="favorite_list_manage">Pārvaldīt atskaņošanas sarakstus</string>
<string name="favorite_list_name_hint">Atskaņošanas saraksta nosaukums</string>
<string name="favorite_list_none_yet">Atskaņošanas sarakstu vēl nav. Izveidojiet vienu, lai sakārtotu izlasi.</string>
<string name="favorite_list_rename">Pārdēvēt atskaņošanas sarakstu</string>

[values-mk]
<string name="accessibility_filter_playlist">Филтер за плејлиста: %1$s</string>
<string name="accessibility_playlist_delete">Избриши %1$s</string>
<string name="accessibility_playlist_edit_stations">Уреди станици во %1$s</string>
<string name="accessibility_playlist_rename">Преименувај %1$s</string>
<string name="action_clear_playlist_filter">Исчисти филтер за плејлиста</string>
<string name="action_manage_station_playlists">Додај во плејлиста</string>
<string name="action_remove_from_playlist">Отстрани од \"%1$s\"</string>
<string name="action_scroll_to_bottom">Лизгај до дното</string>
<string name="favorite_list_add_stations">Додај омилени</string>
<string name="favorite_list_all">Сите</string>
<string name="favorite_list_assign_title">Додај во плејлисти</string>
<string name="favorite_list_create">Нова плејлиста</string>
<string name="favorite_list_delete_message">Да се избрише \"%1$s\"? Вашите станици ќе останат во Омилени.</string>
<string name="favorite_list_delete_title">Избриши плејлиста</string>
<string name="favorite_list_edit_stations">Уреди станици</string>
<string name="favorite_list_empty">Оваа плејлиста е празна</string>
<string name="favorite_list_manage">Управувај со плејлисти</string>
<string name="favorite_list_name_hint">Име на плејлиста</string>
<string name="favorite_list_none_yet">Сè уште нема плејлисти. Создадете една за да ги организирате омилените.</string>
<string name="favorite_list_rename">Преименувај плејлиста</string>

[values-ml]
<string name="accessibility_filter_playlist">പ്ലേലിസ്റ്റ് ഫിൽട്ടർ: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ഇല്ലാതാക്കുക</string>
<string name="accessibility_playlist_edit_stations">%1$s-ലെ സ്റ്റേഷനുകൾ എഡിറ്റ് ചെയ്യുക</string>
<string name="accessibility_playlist_rename">%1$s പേര് മാറ്റുക</string>
<string name="action_clear_playlist_filter">പ്ലേലിസ്റ്റ് ഫിൽട്ടർ മായ്ക്കുക</string>
<string name="action_manage_station_playlists">പ്ലേലിസ്റ്റിലേക്ക് ചേർക്കുക</string>
<string name="action_remove_from_playlist">\"%1$s\" എന്നതിൽ നിന്ന് നീക്കം ചെയ്യുക</string>
<string name="action_scroll_to_bottom">ഏറ്റവും താഴേക്ക് സ്ക്രോൾ ചെയ്യുക</string>
<string name="favorite_list_add_stations">പ്രിയപ്പെട്ടവ ചേർക്കുക</string>
<string name="favorite_list_all">എല്ലാം</string>
<string name="favorite_list_assign_title">പ്ലേലിസ്റ്റുകളിലേക്ക് ചേർക്കുക</string>
<string name="favorite_list_create">പുതിയ പ്ലേലിസ്റ്റ്</string>
<string name="favorite_list_delete_message">\"%1$s\" ഇല്ലാതാക്കണോ? നിങ്ങളുടെ സ്റ്റേഷനുകൾ പ്രിയപ്പെട്ടവയിൽ തന്നെ തുടരും.</string>
<string name="favorite_list_delete_title">പ്ലേലിസ്റ്റ് ഇല്ലാതാക്കുക</string>
<string name="favorite_list_edit_stations">സ്റ്റേഷനുകൾ എഡിറ്റ് ചെയ്യുക</string>
<string name="favorite_list_empty">ഈ പ്ലേലിസ്റ്റ് ശൂന്യമാണ്</string>
<string name="favorite_list_manage">പ്ലേലിസ്റ്റുകൾ നിയന്ത്രിക്കുക</string>
<string name="favorite_list_name_hint">പ്ലേലിസ്റ്റിന്റെ പേര്</string>
<string name="favorite_list_none_yet">ഇതുവരെ പ്ലേലിസ്റ്റുകളൊന്നുമില്ല. നിങ്ങളുടെ പ്രിയപ്പെട്ടവ ക്രമീകരിക്കാൻ ഒരെണ്ണം സൃഷ്ടിക്കുക.</string>
<string name="favorite_list_rename">പ്ലേലിസ്റ്റിന്റെ പേര് മാറ്റുക</string>

[values-mn]
<string name="accessibility_filter_playlist">Тоглуулах жагсаалтын шүүлтүүр: %1$s</string>
<string name="accessibility_playlist_delete">%1$s устгах</string>
<string name="accessibility_playlist_edit_stations">%1$s доторх станцуудыг засах</string>
<string name="accessibility_playlist_rename">%1$s нэрийг өөрчлөх</string>
<string name="action_clear_playlist_filter">Тоглуулах жагсаалтын шүүлтүүр арилгах</string>
<string name="action_manage_station_playlists">Тоглуулах жагсаалтад нэмэх</string>
<string name="action_remove_from_playlist">\"%1$s\"-ээс хасах</string>
<string name="action_scroll_to_bottom">Хамгийн доош гүйлгэх</string>
<string name="favorite_list_add_stations">Дуртай станц нэмэх</string>
<string name="favorite_list_all">Бүгд</string>
<string name="favorite_list_assign_title">Тоглуулах жагсаалтуудад нэмэх</string>
<string name="favorite_list_create">Шинэ тоглуулах жагсаалт</string>
<string name="favorite_list_delete_message">\"%1$s\"-г устгах уу? Таны станцууд Дуртай хэсэгт хэвээр үлдэнэ.</string>
<string name="favorite_list_delete_title">Тоглуулах жагсаалт устгах</string>
<string name="favorite_list_edit_stations">Станцуудыг засах</string>
<string name="favorite_list_empty">Энэ тоглуулах жагсаалт хоосон байна</string>
<string name="favorite_list_manage">Тоглуулах жагсаалтуудыг удирдах</string>
<string name="favorite_list_name_hint">Тоглуулах жагсаалтын нэр</string>
<string name="favorite_list_none_yet">Одоогоор тоглуулах жагсаалт алга. Дуртай станцуудаа эмхлэхийн тулд нэгийг үүсгэнэ үү.</string>
<string name="favorite_list_rename">Тоглуулах жагсаалтын нэр өөрчлөх</string>

[values-mr]
<string name="accessibility_filter_playlist">प्लेलिस्ट फिल्टर: %1$s</string>
<string name="accessibility_playlist_delete">%1$s हटवा</string>
<string name="accessibility_playlist_edit_stations">%1$s मधील स्टेशन्स संपादित करा</string>
<string name="accessibility_playlist_rename">%1$s चे नाव बदला</string>
<string name="action_clear_playlist_filter">प्लेलिस्ट फिल्टर साफ करा</string>
<string name="action_manage_station_playlists">प्लेलिस्टमध्ये जोडा</string>
<string name="action_remove_from_playlist">\"%1$s\" मधून काढा</string>
<string name="action_scroll_to_bottom">सर्वात खाली स्क्रोल करा</string>
<string name="favorite_list_add_stations">आवडते जोडा</string>
<string name="favorite_list_all">सर्व</string>
<string name="favorite_list_assign_title">प्लेलिस्टमध्ये जोडा</string>
<string name="favorite_list_create">नवीन प्लेलिस्ट</string>
<string name="favorite_list_delete_message">\"%1$s\" हटवायची का? तुमची स्टेशन्स आवडत्यांमध्ये राहतील.</string>
<string name="favorite_list_delete_title">प्लेलिस्ट हटवा</string>
<string name="favorite_list_edit_stations">स्टेशन्स संपादित करा</string>
<string name="favorite_list_empty">ही प्लेलिस्ट रिकामी आहे</string>
<string name="favorite_list_manage">प्लेलिस्ट व्यवस्थापित करा</string>
<string name="favorite_list_name_hint">प्लेलिस्टचे नाव</string>
<string name="favorite_list_none_yet">अद्याप कोणतीही प्लेलिस्ट नाही. तुमचे आवडते स्टेशन्स व्यवस्थित ठेवण्यासाठी एक तयार करा.</string>
<string name="favorite_list_rename">प्लेलिस्टचे नाव बदला</string>

[values-ms]
<string name="accessibility_filter_playlist">Penapis senarai main: %1$s</string>
<string name="accessibility_playlist_delete">Padam %1$s</string>
<string name="accessibility_playlist_edit_stations">Edit stesen dalam %1$s</string>
<string name="accessibility_playlist_rename">Namakan semula %1$s</string>
<string name="action_clear_playlist_filter">Kosongkan penapis senarai main</string>
<string name="action_manage_station_playlists">Tambah ke senarai main</string>
<string name="action_remove_from_playlist">Alih keluar daripada \"%1$s\"</string>
<string name="action_scroll_to_bottom">Tatal ke paling bawah</string>
<string name="favorite_list_add_stations">Tambah kegemaran</string>
<string name="favorite_list_all">Semua</string>
<string name="favorite_list_assign_title">Tambah ke senarai main</string>
<string name="favorite_list_create">Senarai main baharu</string>
<string name="favorite_list_delete_message">Padamkan \"%1$s\"? Stesen anda akan kekal dalam Kegemaran.</string>
<string name="favorite_list_delete_title">Padam senarai main</string>
<string name="favorite_list_edit_stations">Edit stesen</string>
<string name="favorite_list_empty">Senarai main ini kosong</string>
<string name="favorite_list_manage">Urus senarai main</string>
<string name="favorite_list_name_hint">Nama senarai main</string>
<string name="favorite_list_none_yet">Belum ada senarai main. Cipta satu untuk menyusun kegemaran anda.</string>
<string name="favorite_list_rename">Namakan semula senarai main</string>

[values-mt]
<string name="accessibility_filter_playlist">Filtru tal-playlist: %1$s</string>
<string name="accessibility_playlist_delete">Ħassar %1$s</string>
<string name="accessibility_playlist_edit_stations">Editja l-istazzjonijiet f\'%1$s</string>
<string name="accessibility_playlist_rename">Semmi mill-ġdid %1$s</string>
<string name="action_clear_playlist_filter">Neħħi l-filtru tal-playlist</string>
<string name="action_manage_station_playlists">Żid mal-playlist</string>
<string name="action_remove_from_playlist">Neħħi minn \"%1$s\"</string>
<string name="action_scroll_to_bottom">Skrollja sal-qiegħ</string>
<string name="favorite_list_add_stations">Żid favoriti</string>
<string name="favorite_list_all">Kollha</string>
<string name="favorite_list_assign_title">Żid mal-playlists</string>
<string name="favorite_list_create">Playlist ġdida</string>
<string name="favorite_list_delete_message">Tħassar \"%1$s\"? L-istazzjonijiet tiegħek jibqgħu fil-Favoriti.</string>
<string name="favorite_list_delete_title">Ħassar playlist</string>
<string name="favorite_list_edit_stations">Editja l-istazzjonijiet</string>
<string name="favorite_list_empty">Din il-playlist hija vojta</string>
<string name="favorite_list_manage">Immaniġġja l-playlists</string>
<string name="favorite_list_name_hint">Isem il-playlist</string>
<string name="favorite_list_none_yet">Għad m\'hemmx playlists. Oħloq waħda biex torganizza l-favoriti tiegħek.</string>
<string name="favorite_list_rename">Semmi mill-ġdid il-playlist</string>

[values-my]
<string name="accessibility_filter_playlist">ဖွင့်စာရင်း စစ်ထုတ်မှု- %1$s</string>
<string name="accessibility_playlist_delete">%1$s ကို ဖျက်ရန်</string>
<string name="accessibility_playlist_edit_stations">%1$s ရှိ အသံလွှင့်ဌာနများကို ပြင်ဆင်ရန်</string>
<string name="accessibility_playlist_rename">%1$s ကို အမည်ပြောင်းရန်</string>
<string name="action_clear_playlist_filter">ဖွင့်စာရင်းစစ်ထုတ်မှု ရှင်းလင်းရန်</string>
<string name="action_manage_station_playlists">ဖွင့်စာရင်းသို့ ထည့်ရန်</string>
<string name="action_remove_from_playlist">\"%1$s\" မှ ဖယ်ရှားရန်</string>
<string name="action_scroll_to_bottom">အောက်ဆုံးသို့ ရွှေ့ရန်</string>
<string name="favorite_list_add_stations">အကြိုက်ဆုံးများ ထည့်ရန်</string>
<string name="favorite_list_all">အားလုံး</string>
<string name="favorite_list_assign_title">ဖွင့်စာရင်းများသို့ ထည့်ရန်</string>
<string name="favorite_list_create">ဖွင့်စာရင်းအသစ်</string>
<string name="favorite_list_delete_message">\"%1$s\" ကို ဖျက်မလား။ သင့်အသံလွှင့်ဌာနများသည် အကြိုက်ဆုံးများထဲတွင် ဆက်ရှိနေပါမည်။</string>
<string name="favorite_list_delete_title">ဖွင့်စာရင်း ဖျက်ရန်</string>
<string name="favorite_list_edit_stations">အသံလွှင့်ဌာနများ ပြင်ဆင်ရန်</string>
<string name="favorite_list_empty">ဤဖွင့်စာရင်းသည် ဗလာဖြစ်နေပါသည်</string>
<string name="favorite_list_manage">ဖွင့်စာရင်းများ စီမံရန်</string>
<string name="favorite_list_name_hint">ဖွင့်စာရင်း အမည်</string>
<string name="favorite_list_none_yet">ဖွင့်စာရင်းများ မရှိသေးပါ။ သင့်အကြိုက်ဆုံးများကို စုစည်းရန် တစ်ခုဖန်တီးပါ။</string>
<string name="favorite_list_rename">ဖွင့်စာရင်း အမည်ပြောင်းရန်</string>

[values-nb]
<string name="accessibility_filter_playlist">Spillelistefilter: %1$s</string>
<string name="accessibility_playlist_delete">Slett %1$s</string>
<string name="accessibility_playlist_edit_stations">Rediger stasjoner i %1$s</string>
<string name="accessibility_playlist_rename">Gi %1$s nytt navn</string>
<string name="action_clear_playlist_filter">Tøm spillelistefilter</string>
<string name="action_manage_station_playlists">Legg til i spilleliste</string>
<string name="action_remove_from_playlist">Fjern fra \"%1$s\"</string>
<string name="action_scroll_to_bottom">Rull til bunnen</string>
<string name="favorite_list_add_stations">Legg til favoritter</string>
<string name="favorite_list_all">Alle</string>
<string name="favorite_list_assign_title">Legg til i spillelister</string>
<string name="favorite_list_create">Ny spilleliste</string>
<string name="favorite_list_delete_message">Slette \"%1$s\"? Stasjonene dine forblir i Favoritter.</string>
<string name="favorite_list_delete_title">Slett spilleliste</string>
<string name="favorite_list_edit_stations">Rediger stasjoner</string>
<string name="favorite_list_empty">Denne spillelisten er tom</string>
<string name="favorite_list_manage">Administrer spillelister</string>
<string name="favorite_list_name_hint">Navn på spilleliste</string>
<string name="favorite_list_none_yet">Ingen spillelister ennå. Opprett en for å organisere favorittene dine.</string>
<string name="favorite_list_rename">Gi spillelisten nytt navn</string>

[values-ne]
<string name="accessibility_filter_playlist">प्लेलिस्ट फिल्टर: %1$s</string>
<string name="accessibility_playlist_delete">%1$s मेटाउनुहोस्</string>
<string name="accessibility_playlist_edit_stations">%1$s मा स्टेसनहरू सम्पादन गर्नुहोस्</string>
<string name="accessibility_playlist_rename">%1$s को नाम फेर्नुहोस्</string>
<string name="action_clear_playlist_filter">प्लेलिस्ट फिल्टर हटाउनुहोस्</string>
<string name="action_manage_station_playlists">प्लेलिस्टमा थप्नुहोस्</string>
<string name="action_remove_from_playlist">\"%1$s\" बाट हटाउनुहोस्</string>
<string name="action_scroll_to_bottom">सबैभन्दा तल स्क्रोल गर्नुहोस्</string>
<string name="favorite_list_add_stations">मनपर्नेहरू थप्नुहोस्</string>
<string name="favorite_list_all">सबै</string>
<string name="favorite_list_assign_title">प्लेलिस्टहरूमा थप्नुहोस्</string>
<string name="favorite_list_create">नयाँ प्लेलिस्ट</string>
<string name="favorite_list_delete_message">\"%1$s\" मेटाउने हो? तपाईंका स्टेसनहरू मनपर्नेमा रहनेछन्।</string>
<string name="favorite_list_delete_title">प्लेलिस्ट मेटाउनुहोस्</string>
<string name="favorite_list_edit_stations">स्टेसनहरू सम्पादन गर्नुहोस्</string>
<string name="favorite_list_empty">यो प्लेलिस्ट खाली छ</string>
<string name="favorite_list_manage">प्लेलिस्टहरू व्यवस्थापन गर्नुहोस्</string>
<string name="favorite_list_name_hint">प्लेलिस्टको नाम</string>
<string name="favorite_list_none_yet">अहिलेसम्म कुनै प्लेलिस्ट छैन। आफ्ना मनपर्नेहरू व्यवस्थित गर्न एउटा सिर्जना गर्नुहोस्।</string>
<string name="favorite_list_rename">प्लेलिस्टको नाम फेर्नुहोस्</string>

[values-nl]
<string name="accessibility_filter_playlist">Afspeellijstfilter: %1$s</string>
<string name="accessibility_playlist_delete">%1$s verwijderen</string>
<string name="accessibility_playlist_edit_stations">Zenders bewerken in %1$s</string>
<string name="accessibility_playlist_rename">Naam van %1$s wijzigen</string>
<string name="action_clear_playlist_filter">Afspeellijstfilter wissen</string>
<string name="action_manage_station_playlists">Toevoegen aan afspeellijst</string>
<string name="action_remove_from_playlist">Verwijderen uit \"%1$s\"</string>
<string name="action_scroll_to_bottom">Naar beneden scrollen</string>
<string name="favorite_list_add_stations">Favorieten toevoegen</string>
<string name="favorite_list_all">Alle</string>
<string name="favorite_list_assign_title">Toevoegen aan afspeellijsten</string>
<string name="favorite_list_create">Nieuwe afspeellijst</string>
<string name="favorite_list_delete_message">\"%1$s\" verwijderen? Uw zenders blijven in Favorieten staan.</string>
<string name="favorite_list_delete_title">Afspeellijst verwijderen</string>
<string name="favorite_list_edit_stations">Zenders bewerken</string>
<string name="favorite_list_empty">Deze afspeellijst is leeg</string>
<string name="favorite_list_manage">Afspeellijsten beheren</string>
<string name="favorite_list_name_hint">Naam van afspeellijst</string>
<string name="favorite_list_none_yet">Nog geen afspeellijsten. Maak er een aan om uw favorieten te ordenen.</string>
<string name="favorite_list_rename">Naam van afspeellijst wijzigen</string>

[values-nn]
<string name="accessibility_filter_playlist">Spelelistefilter: %1$s</string>
<string name="accessibility_playlist_delete">Slett %1$s</string>
<string name="accessibility_playlist_edit_stations">Rediger stasjonar i %1$s</string>
<string name="accessibility_playlist_rename">Gje %1$s nytt namn</string>
<string name="action_clear_playlist_filter">Tøm spelelistefilter</string>
<string name="action_manage_station_playlists">Legg til i speleliste</string>
<string name="action_remove_from_playlist">Fjern frå \"%1$s\"</string>
<string name="action_scroll_to_bottom">Rull til botnen</string>
<string name="favorite_list_add_stations">Legg til favorittar</string>
<string name="favorite_list_all">Alle</string>
<string name="favorite_list_assign_title">Legg til i spelelister</string>
<string name="favorite_list_create">Ny speleliste</string>
<string name="favorite_list_delete_message">Slette \"%1$s\"? Stasjonane dine vert verande i Favorittar.</string>
<string name="favorite_list_delete_title">Slett speleliste</string>
<string name="favorite_list_edit_stations">Rediger stasjonar</string>
<string name="favorite_list_empty">Denne spelelista er tom</string>
<string name="favorite_list_manage">Administrer spelelister</string>
<string name="favorite_list_name_hint">Namn på speleliste</string>
<string name="favorite_list_none_yet">Ingen spelelister enno. Opprett ei for å organisere favorittane dine.</string>
<string name="favorite_list_rename">Gje spelelista nytt namn</string>

[values-or]
<string name="accessibility_filter_playlist">ପ୍ଲେଲିଷ୍ଟ ଫିଲ୍ଟର୍: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ଡିଲିଟ୍ କରନ୍ତୁ</string>
<string name="accessibility_playlist_edit_stations">%1$s ରେ ଷ୍ଟେସନ୍ ସମ୍ପାଦନ କରନ୍ତୁ</string>
<string name="accessibility_playlist_rename">%1$s ର ନାମ ପରିବର୍ତ୍ତନ କରନ୍ତୁ</string>
<string name="action_clear_playlist_filter">ପ୍ଲେଲିଷ୍ଟ ଫିଲ୍ଟର୍ ସଫା କରନ୍ତୁ</string>
<string name="action_manage_station_playlists">ପ୍ଲେଲିଷ୍ଟରେ ଯୋଡ଼ନ୍ତୁ</string>
<string name="action_remove_from_playlist">\"%1$s\" ରୁ ହଟାନ୍ତୁ</string>
<string name="action_scroll_to_bottom">ସବୁଠାରୁ ତଳକୁ ସ୍କ୍ରୋଲ୍ କରନ୍ତୁ</string>
<string name="favorite_list_add_stations">ପସନ୍ଦଗୁଡ଼ିକ ଯୋଡ଼ନ୍ତୁ</string>
<string name="favorite_list_all">ସମସ୍ତ</string>
<string name="favorite_list_assign_title">ପ୍ଲେଲିଷ୍ଟଗୁଡ଼ିକରେ ଯୋଡ଼ନ୍ତୁ</string>
<string name="favorite_list_create">ନୂଆ ପ୍ଲେଲିଷ୍ଟ</string>
<string name="favorite_list_delete_message">\"%1$s\" ଡିଲିଟ୍ କରିବେ କି? ଆପଣଙ୍କ ଷ୍ଟେସନ୍‌ଗୁଡ଼ିକ ପସନ୍ଦରେ ରହିବ।</string>
<string name="favorite_list_delete_title">ପ୍ଲେଲିଷ୍ଟ ଡିଲିଟ୍ କରନ୍ତୁ</string>
<string name="favorite_list_edit_stations">ଷ୍ଟେସନ୍ ସମ୍ପାଦନ କରନ୍ତୁ</string>
<string name="favorite_list_empty">ଏହି ପ୍ଲେଲିଷ୍ଟ ଖାଲି ଅଛି</string>
<string name="favorite_list_manage">ପ୍ଲେଲିଷ୍ଟ ପରିଚାଳନା କରନ୍ତୁ</string>
<string name="favorite_list_name_hint">ପ୍ଲେଲିଷ୍ଟ ନାମ</string>
<string name="favorite_list_none_yet">ଏପର୍ଯ୍ୟନ୍ତ କୌଣସି ପ୍ଲେଲିଷ୍ଟ ନାହିଁ। ଆପଣଙ୍କ ପସନ୍ଦଗୁଡ଼ିକ ସଜାଇବା ପାଇଁ ଗୋଟିଏ ତିଆରି କରନ୍ତୁ।</string>
<string name="favorite_list_rename">ପ୍ଲେଲିଷ୍ଟ ନାମ ପରିବର୍ତ୍ତନ କରନ୍ତୁ</string>

[values-pa]
<string name="accessibility_filter_playlist">ਪਲੇਲਿਸਟ ਫਿਲਟਰ: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ਮਿਟਾਓ</string>
<string name="accessibility_playlist_edit_stations">%1$s ਵਿੱਚ ਸਟੇਸ਼ਨ ਸੰਪਾਦਿਤ ਕਰੋ</string>
<string name="accessibility_playlist_rename">%1$s ਦਾ ਨਾਮ ਬਦਲੋ</string>
<string name="action_clear_playlist_filter">ਪਲੇਲਿਸਟ ਫਿਲਟਰ ਸਾਫ਼ ਕਰੋ</string>
<string name="action_manage_station_playlists">ਪਲੇਲਿਸਟ ਵਿੱਚ ਸ਼ਾਮਲ ਕਰੋ</string>
<string name="action_remove_from_playlist">\"%1$s\" ਤੋਂ ਹਟਾਓ</string>
<string name="action_scroll_to_bottom">ਸਭ ਤੋਂ ਹੇਠਾਂ ਸਕ੍ਰੋਲ ਕਰੋ</string>
<string name="favorite_list_add_stations">ਮਨਪਸੰਦ ਸ਼ਾਮਲ ਕਰੋ</string>
<string name="favorite_list_all">ਸਾਰੇ</string>
<string name="favorite_list_assign_title">ਪਲੇਲਿਸਟਾਂ ਵਿੱਚ ਸ਼ਾਮਲ ਕਰੋ</string>
<string name="favorite_list_create">ਨਵੀਂ ਪਲੇਲਿਸਟ</string>
<string name="favorite_list_delete_message">\"%1$s\" ਮਿਟਾਉਣੀ ਹੈ? ਤੁਹਾਡੇ ਸਟੇਸ਼ਨ ਮਨਪਸੰਦ ਵਿੱਚ ਰਹਿਣਗੇ।</string>
<string name="favorite_list_delete_title">ਪਲੇਲਿਸਟ ਮਿਟਾਓ</string>
<string name="favorite_list_edit_stations">ਸਟੇਸ਼ਨ ਸੰਪਾਦਿਤ ਕਰੋ</string>
<string name="favorite_list_empty">ਇਹ ਪਲੇਲਿਸਟ ਖਾਲੀ ਹੈ</string>
<string name="favorite_list_manage">ਪਲੇਲਿਸਟਾਂ ਦਾ ਪ੍ਰਬੰਧਨ ਕਰੋ</string>
<string name="favorite_list_name_hint">ਪਲੇਲਿਸਟ ਦਾ ਨਾਮ</string>
<string name="favorite_list_none_yet">ਅਜੇ ਤੱਕ ਕੋਈ ਪਲੇਲਿਸਟ ਨਹੀਂ ਹੈ। ਆਪਣੇ ਮਨਪਸੰਦ ਸਟੇਸ਼ਨ ਵਿਵਸਥਿਤ ਕਰਨ ਲਈ ਇੱਕ ਬਣਾਓ।</string>
<string name="favorite_list_rename">ਪਲੇਲਿਸਟ ਦਾ ਨਾਮ ਬਦਲੋ</string>

[values-pl]
<string name="accessibility_filter_playlist">Filtr playlisty: %1$s</string>
<string name="accessibility_playlist_delete">Usuń %1$s</string>
<string name="accessibility_playlist_edit_stations">Edytuj stacje na playliście %1$s</string>
<string name="accessibility_playlist_rename">Zmień nazwę %1$s</string>
<string name="action_clear_playlist_filter">Wyczyść filtr playlisty</string>
<string name="action_manage_station_playlists">Dodaj do playlisty</string>
<string name="action_remove_from_playlist">Usuń z „%1$s”</string>
<string name="action_scroll_to_bottom">Przewiń na dół</string>
<string name="favorite_list_add_stations">Dodaj ulubione</string>
<string name="favorite_list_all">Wszystkie</string>
<string name="favorite_list_assign_title">Dodaj do playlist</string>
<string name="favorite_list_create">Nowa playlista</string>
<string name="favorite_list_delete_message">Usunąć „%1$s”? Twoje stacje pozostaną w Ulubionych.</string>
<string name="favorite_list_delete_title">Usuń playlistę</string>
<string name="favorite_list_edit_stations">Edytuj stacje</string>
<string name="favorite_list_empty">Ta playlista jest pusta</string>
<string name="favorite_list_manage">Zarządzaj playlistami</string>
<string name="favorite_list_name_hint">Nazwa playlisty</string>
<string name="favorite_list_none_yet">Brak playlist. Utwórz pierwszą, aby uporządkować ulubione.</string>
<string name="favorite_list_rename">Zmień nazwę playlisty</string>

[values-ps]
<string name="accessibility_filter_playlist">د پلې لېست فلټر: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ړنګول</string>
<string name="accessibility_playlist_edit_stations">په %1$s کې سټېشنونه سمول</string>
<string name="accessibility_playlist_rename">د %1$s نوم بدلول</string>
<string name="action_clear_playlist_filter">د پلې لېست فلټر پاکول</string>
<string name="action_manage_station_playlists">پلې لېست ته ورزیاتول</string>
<string name="action_remove_from_playlist">له \"%1$s\" څخه لرې کول</string>
<string name="action_scroll_to_bottom">تر ټولو ښکته سکرول کول</string>
<string name="favorite_list_add_stations">خوښې ورزیاتول</string>
<string name="favorite_list_all">ټول</string>
<string name="favorite_list_assign_title">پلې لېستونو ته ورزیاتول</string>
<string name="favorite_list_create">نوی پلې لېست</string>
<string name="favorite_list_delete_message">\"%1$s\" ړنګ شي؟ ستاسو سټېشنونه به په خوښو کې پاتې شي.</string>
<string name="favorite_list_delete_title">پلې لېست ړنګول</string>
<string name="favorite_list_edit_stations">سټېشنونه سمول</string>
<string name="favorite_list_empty">دا پلې لېست تش دی</string>
<string name="favorite_list_manage">د پلې لېستونو اداره کول</string>
<string name="favorite_list_name_hint">د پلې لېست نوم</string>
<string name="favorite_list_none_yet">تر اوسه هېڅ پلې لېست نشته. د خپلو خوښو د تنظیمولو لپاره یو جوړ کړئ.</string>
<string name="favorite_list_rename">د پلې لېست نوم بدلول</string>

[values-pt]
<string name="accessibility_filter_playlist">Filtro da lista de reprodução: %1$s</string>
<string name="accessibility_playlist_delete">Eliminar %1$s</string>
<string name="accessibility_playlist_edit_stations">Editar estações em %1$s</string>
<string name="accessibility_playlist_rename">Mudar o nome de %1$s</string>
<string name="action_clear_playlist_filter">Limpar filtro da lista de reprodução</string>
<string name="action_manage_station_playlists">Adicionar à lista de reprodução</string>
<string name="action_remove_from_playlist">Remover de \"%1$s\"</string>
<string name="action_scroll_to_bottom">Deslocar para o fundo</string>
<string name="favorite_list_add_stations">Adicionar favoritos</string>
<string name="favorite_list_all">Todas</string>
<string name="favorite_list_assign_title">Adicionar às listas de reprodução</string>
<string name="favorite_list_create">Nova lista de reprodução</string>
<string name="favorite_list_delete_message">Eliminar \"%1$s\"? As suas estações permanecerão nos Favoritos.</string>
<string name="favorite_list_delete_title">Eliminar lista de reprodução</string>
<string name="favorite_list_edit_stations">Editar estações</string>
<string name="favorite_list_empty">Esta lista de reprodução está vazia</string>
<string name="favorite_list_manage">Gerir listas de reprodução</string>
<string name="favorite_list_name_hint">Nome da lista de reprodução</string>
<string name="favorite_list_none_yet">Ainda não existem listas de reprodução. Crie uma para organizar os seus favoritos.</string>
<string name="favorite_list_rename">Mudar nome da lista de reprodução</string>

[values-pt-rBR]
<string name="accessibility_filter_playlist">Filtro da playlist: %1$s</string>
<string name="accessibility_playlist_delete">Excluir %1$s</string>
<string name="accessibility_playlist_edit_stations">Editar estações em %1$s</string>
<string name="accessibility_playlist_rename">Renomear %1$s</string>
<string name="action_clear_playlist_filter">Limpar filtro da playlist</string>
<string name="action_manage_station_playlists">Adicionar à playlist</string>
<string name="action_remove_from_playlist">Remover de \"%1$s\"</string>
<string name="action_scroll_to_bottom">Rolar para o final</string>
<string name="favorite_list_add_stations">Adicionar favoritos</string>
<string name="favorite_list_all">Todas</string>
<string name="favorite_list_assign_title">Adicionar às playlists</string>
<string name="favorite_list_create">Nova playlist</string>
<string name="favorite_list_delete_message">Excluir \"%1$s\"? Suas estações permanecerão nos Favoritos.</string>
<string name="favorite_list_delete_title">Excluir playlist</string>
<string name="favorite_list_edit_stations">Editar estações</string>
<string name="favorite_list_empty">Esta playlist está vazia</string>
<string name="favorite_list_manage">Gerenciar playlists</string>
<string name="favorite_list_name_hint">Nome da playlist</string>
<string name="favorite_list_none_yet">Nenhuma playlist ainda. Crie uma para organizar seus favoritos.</string>
<string name="favorite_list_rename">Renomear playlist</string>

[values-rm]
<string name="accessibility_filter_playlist">Filter da playlist: %1$s</string>
<string name="accessibility_playlist_delete">Stizzar %1$s</string>
<string name="accessibility_playlist_edit_stations">Modifitgar emetturs en %1$s</string>
<string name="accessibility_playlist_rename">Renumnar %1$s</string>
<string name="action_clear_playlist_filter">Stizzar il filter da playlist</string>
<string name="action_manage_station_playlists">Agiuntar a la playlist</string>
<string name="action_remove_from_playlist">Allontanar da «%1$s»</string>
<string name="action_scroll_to_bottom">Scrollar tut engiu</string>
<string name="favorite_list_add_stations">Agiuntar favurits</string>
<string name="favorite_list_all">Tut</string>
<string name="favorite_list_assign_title">Agiuntar a playlists</string>
<string name="favorite_list_create">Nova playlist</string>
<string name="favorite_list_delete_message">Stizzar «%1$s»? Vos emetturs restan en ils favurits.</string>
<string name="favorite_list_delete_title">Stizzar la playlist</string>
<string name="favorite_list_edit_stations">Modifitgar emetturs</string>
<string name="favorite_list_empty">Questa playlist è vida</string>
<string name="favorite_list_manage">Administrar playlists</string>
<string name="favorite_list_name_hint">Num da la playlist</string>
<string name="favorite_list_none_yet">Anc naginas playlists. Cree ina per organisar Vos favurits.</string>
<string name="favorite_list_rename">Renumnar la playlist</string>

[values-ro]
<string name="accessibility_filter_playlist">Filtru playlist: %1$s</string>
<string name="accessibility_playlist_delete">Șterge %1$s</string>
<string name="accessibility_playlist_edit_stations">Editează posturile din %1$s</string>
<string name="accessibility_playlist_rename">Redenumește %1$s</string>
<string name="action_clear_playlist_filter">Șterge filtrul de playlist</string>
<string name="action_manage_station_playlists">Adaugă în playlist</string>
<string name="action_remove_from_playlist">Elimină din \"%1$s\"</string>
<string name="action_scroll_to_bottom">Derulează până jos</string>
<string name="favorite_list_add_stations">Adaugă favorite</string>
<string name="favorite_list_all">Toate</string>
<string name="favorite_list_assign_title">Adaugă în playlisturi</string>
<string name="favorite_list_create">Playlist nou</string>
<string name="favorite_list_delete_message">Ștergeți \"%1$s\"? Posturile dvs. vor rămâne la Favorite.</string>
<string name="favorite_list_delete_title">Șterge playlistul</string>
<string name="favorite_list_edit_stations">Editează posturile</string>
<string name="favorite_list_empty">Acest playlist este gol</string>
<string name="favorite_list_manage">Gestionează playlisturile</string>
<string name="favorite_list_name_hint">Numele playlistului</string>
<string name="favorite_list_none_yet">Încă nu există playlisturi. Creați unul pentru a vă organiza favoritele.</string>
<string name="favorite_list_rename">Redenumește playlistul</string>

[values-ru]
<string name="accessibility_filter_playlist">Фильтр плейлиста: %1$s</string>
<string name="accessibility_playlist_delete">Удалить %1$s</string>
<string name="accessibility_playlist_edit_stations">Редактировать станции в %1$s</string>
<string name="accessibility_playlist_rename">Переименовать %1$s</string>
<string name="action_clear_playlist_filter">Сбросить фильтр плейлиста</string>
<string name="action_manage_station_playlists">Добавить в плейлист</string>
<string name="action_remove_from_playlist">Удалить из «%1$s»</string>
<string name="action_scroll_to_bottom">Прокрутить вниз</string>
<string name="favorite_list_add_stations">Добавить избранное</string>
<string name="favorite_list_all">Все</string>
<string name="favorite_list_assign_title">Добавить в плейлисты</string>
<string name="favorite_list_create">Новый плейлист</string>
<string name="favorite_list_delete_message">Удалить «%1$s»? Ваши станции останутся в Избранном.</string>
<string name="favorite_list_delete_title">Удалить плейлист</string>
<string name="favorite_list_edit_stations">Редактировать станции</string>
<string name="favorite_list_empty">Этот плейлист пуст</string>
<string name="favorite_list_manage">Управление плейлистами</string>
<string name="favorite_list_name_hint">Название плейлиста</string>
<string name="favorite_list_none_yet">Плейлистов пока нет. Создайте один, чтобы упорядочить избранное.</string>
<string name="favorite_list_rename">Переименовать плейлист</string>

[values-si]
<string name="accessibility_filter_playlist">ධාවන ලැයිස්තු පෙරහන: %1$s</string>
<string name="accessibility_playlist_delete">%1$s මකන්න</string>
<string name="accessibility_playlist_edit_stations">%1$s හි නාලිකා සංස්කරණය කරන්න</string>
<string name="accessibility_playlist_rename">%1$s නැවත නම් කරන්න</string>
<string name="action_clear_playlist_filter">ධාවන ලැයිස්තු පෙරහන ඉවත් කරන්න</string>
<string name="action_manage_station_playlists">ධාවන ලැයිස්තුවට එක් කරන්න</string>
<string name="action_remove_from_playlist">\"%1$s\" වෙතින් ඉවත් කරන්න</string>
<string name="action_scroll_to_bottom">පහළටම අනුචලනය කරන්න</string>
<string name="favorite_list_add_stations">ප්‍රියතමයන් එක් කරන්න</string>
<string name="favorite_list_all">සියල්ල</string>
<string name="favorite_list_assign_title">ධාවන ලැයිස්තු වෙත එක් කරන්න</string>
<string name="favorite_list_create">නව ධාවන ලැයිස්තුව</string>
<string name="favorite_list_delete_message">\"%1$s\" මකන්නද? ඔබේ නාලිකා ප්‍රියතමයන් තුළ පවතිනු ඇත.</string>
<string name="favorite_list_delete_title">ධාවන ලැයිස්තුව මකන්න</string>
<string name="favorite_list_edit_stations">නාලිකා සංස්කරණය කරන්න</string>
<string name="favorite_list_empty">මෙම ධාවන ලැයිස්තුව හිස්ය</string>
<string name="favorite_list_manage">ධාවන ලැයිස්තු කළමනාකරණය කරන්න</string>
<string name="favorite_list_name_hint">ධාවන ලැයිස්තුවේ නම</string>
<string name="favorite_list_none_yet">තවමත් ධාවන ලැයිස්තු නොමැත. ඔබේ ප්‍රියතමයන් සංවිධානය කිරීමට එකක් සාදන්න.</string>
<string name="favorite_list_rename">ධාවන ලැයිස්තුව නැවත නම් කරන්න</string>

[values-sk]
<string name="accessibility_filter_playlist">Filter zoznamu skladieb: %1$s</string>
<string name="accessibility_playlist_delete">Odstrániť %1$s</string>
<string name="accessibility_playlist_edit_stations">Upraviť stanice v %1$s</string>
<string name="accessibility_playlist_rename">Premenovať %1$s</string>
<string name="action_clear_playlist_filter">Vymazať filter zoznamu skladieb</string>
<string name="action_manage_station_playlists">Pridať do zoznamu skladieb</string>
<string name="action_remove_from_playlist">Odstrániť z \"%1$s\"</string>
<string name="action_scroll_to_bottom">Posunúť nadol</string>
<string name="favorite_list_add_stations">Pridať obľúbené</string>
<string name="favorite_list_all">Všetky</string>
<string name="favorite_list_assign_title">Pridať do zoznamov skladieb</string>
<string name="favorite_list_create">Nový zoznam skladieb</string>
<string name="favorite_list_delete_message">Odstrániť \"%1$s\"? Vaše stanice zostanú v Obľúbených.</string>
<string name="favorite_list_delete_title">Odstrániť zoznam skladieb</string>
<string name="favorite_list_edit_stations">Upraviť stanice</string>
<string name="favorite_list_empty">Tento zoznam skladieb je prázdný</string>
<string name="favorite_list_manage">Spravovať zoznamy skladieb</string>
<string name="favorite_list_name_hint">Názov zoznamu skladieb</string>
<string name="favorite_list_none_yet">Zatiaľ žiadne zoznamy skladieb. Vytvorte jeden na usporiadanie obľúbených.</string>
<string name="favorite_list_rename">Premenovať zoznam skladieb</string>

[values-sl]
<string name="accessibility_filter_playlist">Filter seznama predvajanja: %1$s</string>
<string name="accessibility_playlist_delete">Izbriši %1$s</string>
<string name="accessibility_playlist_edit_stations">Uredi postaje v %1$s</string>
<string name="accessibility_playlist_rename">Preimenuj %1$s</string>
<string name="action_clear_playlist_filter">Počisti filter seznama predvajanja</string>
<string name="action_manage_station_playlists">Dodaj na seznam predvajanja</string>
<string name="action_remove_from_playlist">Odstrani iz \"%1$s\"</string>
<string name="action_scroll_to_bottom">Pomakni na dno</string>
<string name="favorite_list_add_stations">Dodaj priljubljene</string>
<string name="favorite_list_all">Vse</string>
<string name="favorite_list_assign_title">Dodaj na sezname predvajanja</string>
<string name="favorite_list_create">Nov seznam predvajanja</string>
<string name="favorite_list_delete_message">Izbrisati \"%1$s\"? Vaše postaje bodo ostale med Priljubljenimi.</string>
<string name="favorite_list_delete_title">Izbriši seznam predvajanja</string>
<string name="favorite_list_edit_stations">Uredi postaje</string>
<string name="favorite_list_empty">Ta seznam predvajanja je prazen</string>
<string name="favorite_list_manage">Upravljaj sezname predvajanja</string>
<string name="favorite_list_name_hint">Ime seznama predvajanja</string>
<string name="favorite_list_none_yet">Še ni seznamov predvajanja. Ustvarite enega za organizacijo priljubljenih.</string>
<string name="favorite_list_rename">Preimenuj seznam predvajanja</string>

[values-sq]
<string name="accessibility_filter_playlist">Filtri i listës së luajtjes: %1$s</string>
<string name="accessibility_playlist_delete">Fshi %1$s</string>
<string name="accessibility_playlist_edit_stations">Ndrysho stacionet te %1$s</string>
<string name="accessibility_playlist_rename">Riemërto %1$s</string>
<string name="action_clear_playlist_filter">Pastro filtrin e listës së luajtjes</string>
<string name="action_manage_station_playlists">Shto në listën e luajtjes</string>
<string name="action_remove_from_playlist">Hiq nga \"%1$s\"</string>
<string name="action_scroll_to_bottom">Lëviz në fund</string>
<string name="favorite_list_add_stations">Shto të preferuarat</string>
<string name="favorite_list_all">Të gjitha</string>
<string name="favorite_list_assign_title">Shto në listat e luajtjes</string>
<string name="favorite_list_create">Listë e re luajtjeje</string>
<string name="favorite_list_delete_message">Të fshihet \"%1$s\"? Stacionet tuaja do të mbeten te Të preferuarat.</string>
<string name="favorite_list_delete_title">Fshi listën e luajtjes</string>
<string name="favorite_list_edit_stations">Ndrysho stacionet</string>
<string name="favorite_list_empty">Kjo listë luajtjeje është bosh</string>
<string name="favorite_list_manage">Menaxho listat e luajtjes</string>
<string name="favorite_list_name_hint">Emri i listës së luajtjes</string>
<string name="favorite_list_none_yet">Nuk ka ende lista luajtjeje. Krijoni një për të organizuar të preferuarat tuaja.</string>
<string name="favorite_list_rename">Riemërto listën e luajtjes</string>

[values-sr]
<string name="accessibility_filter_playlist">Филтер плејлисте: %1$s</string>
<string name="accessibility_playlist_delete">Избриши %1$s</string>
<string name="accessibility_playlist_edit_stations">Уреди станице у %1$s</string>
<string name="accessibility_playlist_rename">Преименуј %1$s</string>
<string name="action_clear_playlist_filter">Обриши филтер плејлисте</string>
<string name="action_manage_station_playlists">Додај на плејлисту</string>
<string name="action_remove_from_playlist">Уклони из „%1$s“</string>
<string name="action_scroll_to_bottom">Помери на дно</string>
<string name="favorite_list_add_stations">Додај омиљене</string>
<string name="favorite_list_all">Све</string>
<string name="favorite_list_assign_title">Додај на плејлисте</string>
<string name="favorite_list_create">Нова плејлиста</string>
<string name="favorite_list_delete_message">Избрисати „%1$s“? Ваше станице ће остати у Омиљеним.</string>
<string name="favorite_list_delete_title">Избриши плејлисту</string>
<string name="favorite_list_edit_stations">Уреди станице</string>
<string name="favorite_list_empty">Ова плејлиста је празна</string>
<string name="favorite_list_manage">Управљај плејлистама</string>
<string name="favorite_list_name_hint">Назив плејлисте</string>
<string name="favorite_list_none_yet">Још нема плејлиста. Направите једну да бисте организовали омиљене.</string>
<string name="favorite_list_rename">Преименуј плејлисту</string>

[values-sv]
<string name="accessibility_filter_playlist">Spellistefilter: %1$s</string>
<string name="accessibility_playlist_delete">Ta bort %1$s</string>
<string name="accessibility_playlist_edit_stations">Redigera stationer i %1$s</string>
<string name="accessibility_playlist_rename">Byt namn på %1$s</string>
<string name="action_clear_playlist_filter">Rensa spellistefilter</string>
<string name="action_manage_station_playlists">Lägg till i spellista</string>
<string name="action_remove_from_playlist">Ta bort från \"%1$s\"</string>
<string name="action_scroll_to_bottom">Bläddra längst ner</string>
<string name="favorite_list_add_stations">Lägg till favoriter</string>
<string name="favorite_list_all">Alla</string>
<string name="favorite_list_assign_title">Lägg till i spellistor</string>
<string name="favorite_list_create">Ny spellista</string>
<string name="favorite_list_delete_message">Ta bort \"%1$s\"? Dina stationer finns kvar i Favoriter.</string>
<string name="favorite_list_delete_title">Ta bort spellista</string>
<string name="favorite_list_edit_stations">Redigera stationer</string>
<string name="favorite_list_empty">Den här spellistan är tom</string>
<string name="favorite_list_manage">Hantera spellistor</string>
<string name="favorite_list_name_hint">Spellistans namn</string>
<string name="favorite_list_none_yet">Inga spellistor ännu. Skapa en för att organisera dina favoriter.</string>
<string name="favorite_list_rename">Byt namn på spellista</string>

[values-sw]
<string name="accessibility_filter_playlist">Kichujio cha orodha ya kucheza: %1$s</string>
<string name="accessibility_playlist_delete">Futa %1$s</string>
<string name="accessibility_playlist_edit_stations">Hariri vituo katika %1$s</string>
<string name="accessibility_playlist_rename">Badilisha jina la %1$s</string>
<string name="action_clear_playlist_filter">Futa kichujio cha orodha ya kucheza</string>
<string name="action_manage_station_playlists">Ongeza kwenye orodha ya kucheza</string>
<string name="action_remove_from_playlist">Ondoa kwenye \"%1$s\"</string>
<string name="action_scroll_to_bottom">Sogeza hadi chini kabisa</string>
<string name="favorite_list_add_stations">Ongeza vipendwa</string>
<string name="favorite_list_all">Vyote</string>
<string name="favorite_list_assign_title">Ongeza kwenye orodha za kucheza</string>
<string name="favorite_list_create">Orodha mpya ya kucheza</string>
<string name="favorite_list_delete_message">Ungependa kufuta \"%1$s\"? Vituo vyako vitasalia katika Vipendwa.</string>
<string name="favorite_list_delete_title">Futa orodha ya kucheza</string>
<string name="favorite_list_edit_stations">Hariri vituo</string>
<string name="favorite_list_empty">Orodha hii ya kucheza haina kitu</string>
<string name="favorite_list_manage">Dhibiti orodha za kucheza</string>
<string name="favorite_list_name_hint">Jina la orodha ya kucheza</string>
<string name="favorite_list_none_yet">Bado hakuna orodha za kucheza. Unda moja ili kupanga vipendwa vyako.</string>
<string name="favorite_list_rename">Badilisha jina la orodha ya kucheza</string>

[values-ta]
<string name="accessibility_filter_playlist">பிளேலிஸ்ட் வடிகட்டி: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ஐ நீக்கு</string>
<string name="accessibility_playlist_edit_stations">%1$s இல் உள்ள நிலையங்களைத் திருத்து</string>
<string name="accessibility_playlist_rename">%1$s பெயரை மாற்று</string>
<string name="action_clear_playlist_filter">பிளேலிஸ்ட் வடிகட்டியை அழி</string>
<string name="action_manage_station_playlists">பிளேலிஸ்ட்டில் சேர்</string>
<string name="action_remove_from_playlist">\"%1$s\" இலிருந்து அகற்று</string>
<string name="action_scroll_to_bottom">கீழே உருட்டு</string>
<string name="favorite_list_add_stations">பிடித்தவற்றைச் சேர்</string>
<string name="favorite_list_all">அனைத்தும்</string>
<string name="favorite_list_assign_title">பிளேலிஸ்ட்களில் சேர்</string>
<string name="favorite_list_create">புதிய பிளேலிஸ்ட்</string>
<string name="favorite_list_delete_message">\"%1$s\" ஐ நீக்கவா? உங்கள் நிலையங்கள் பிடித்தவையில் இருக்கும்.</string>
<string name="favorite_list_delete_title">பிளேலிஸ்ட்டை நீக்கு</string>
<string name="favorite_list_edit_stations">நிலையங்களைத் திருத்து</string>
<string name="favorite_list_empty">இந்த பிளேலிஸ்ட் காலியாக உள்ளது</string>
<string name="favorite_list_manage">பிளேலிஸ்ட்களை நிர்வகி</string>
<string name="favorite_list_name_hint">பிளேலிஸ்ட் பெயர்</string>
<string name="favorite_list_none_yet">இதுவரை பிளேலிஸ்ட்கள் இல்லை. உங்கள் பிடித்தவற்றை ஒழுங்கமைக்க ஒன்றை உருவாக்கவும்.</string>
<string name="favorite_list_rename">பிளேலிஸ்ட் பெயரை மாற்று</string>

[values-te]
<string name="accessibility_filter_playlist">ప్లేలిస్ట్ ఫిల్టర్: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ని తొలగించు</string>
<string name="accessibility_playlist_edit_stations">%1$s లోని స్టేషన్లను సవరించు</string>
<string name="accessibility_playlist_rename">%1$s పేరు మార్చు</string>
<string name="action_clear_playlist_filter">ప్లేలిస్ట్ ఫిల్టర్‌ను క్లియర్ చేయి</string>
<string name="action_manage_station_playlists">ప్లేలిస్ట్‌కు జోడించు</string>
<string name="action_remove_from_playlist">\"%1$s\" నుండి తీసివేయి</string>
<string name="action_scroll_to_bottom">అడుగుకు స్క్రోల్ చేయి</string>
<string name="favorite_list_add_stations">ఇష్టమైనవి జోడించు</string>
<string name="favorite_list_all">అన్నీ</string>
<string name="favorite_list_assign_title">ప్లేలిస్ట్‌లకు జోడించు</string>
<string name="favorite_list_create">కొత్త ప్లేలిస్ట్</string>
<string name="favorite_list_delete_message">\"%1$s\" ని తొలగించాలా? మీ స్టేషన్లు ఇష్టమైన వాటిలో అలాగే ఉంటాయి.</string>
<string name="favorite_list_delete_title">ప్లేలిస్ట్‌ను తొలగించు</string>
<string name="favorite_list_edit_stations">స్టేషన్లను సవరించు</string>
<string name="favorite_list_empty">ఈ ప్లేలిస్ట్ ఖాళీగా ఉంది</string>
<string name="favorite_list_manage">ప్లేలిస్ట్‌లను నిర్వహించు</string>
<string name="favorite_list_name_hint">ప్లేలిస్ట్ పేరు</string>
<string name="favorite_list_none_yet">ఇంకా ప్లేలిస్ట్‌లు లేవు. మీ ఇష్టమైన వాటిని నిర్వహించడానికి ఒకదాన్ని సృష్టించండి.</string>
<string name="favorite_list_rename">ప్లేలిస్ట్ పేరు మార్చు</string>

[values-th]
<string name="accessibility_filter_playlist">ตัวกรองเพลย์ลิสต์: %1$s</string>
<string name="accessibility_playlist_delete">ลบ %1$s</string>
<string name="accessibility_playlist_edit_stations">แก้ไขสถานีใน %1$s</string>
<string name="accessibility_playlist_rename">เปลี่ยนชื่อ %1$s</string>
<string name="action_clear_playlist_filter">ล้างตัวกรองเพลย์ลิสต์</string>
<string name="action_manage_station_playlists">เพิ่มลงในเพลย์ลิสต์</string>
<string name="action_remove_from_playlist">ลบออกจาก \"%1$s\"</string>
<string name="action_scroll_to_bottom">เลื่อนไปล่างสุด</string>
<string name="favorite_list_add_stations">เพิ่มรายการโปรด</string>
<string name="favorite_list_all">ทั้งหมด</string>
<string name="favorite_list_assign_title">เพิ่มลงในเพลย์ลิสต์</string>
<string name="favorite_list_create">เพลย์ลิสต์ใหม่</string>
<string name="favorite_list_delete_message">ลบ \"%1$s\" หรือไม่? สถานีของคุณจะยังคงอยู่ในรายการโปรด</string>
<string name="favorite_list_delete_title">ลบเพลย์ลิสต์</string>
<string name="favorite_list_edit_stations">แก้ไขสถานี</string>
<string name="favorite_list_empty">เพลย์ลิสต์นี้ว่างเปล่า</string>
<string name="favorite_list_manage">จัดการเพลย์ลิสต์</string>
<string name="favorite_list_name_hint">ชื่อเพลย์ลิสต์</string>
<string name="favorite_list_none_yet">ยังไม่มีเพลย์ลิสต์ สร้างเพลย์ลิสต์เพื่อจัดระเบียบรายการโปรดของคุณ</string>
<string name="favorite_list_rename">เปลี่ยนชื่อเพลย์ลิสต์</string>

[values-tr]
<string name="accessibility_filter_playlist">Oynatma listesi filtresi: %1$s</string>
<string name="accessibility_playlist_delete">%1$s listesini sil</string>
<string name="accessibility_playlist_edit_stations">%1$s içindeki istasyonları düzenle</string>
<string name="accessibility_playlist_rename">%1$s listesini yeniden adlandır</string>
<string name="action_clear_playlist_filter">Oynatma listesi filtresini temizle</string>
<string name="action_manage_station_playlists">Oynatma listesine ekle</string>
<string name="action_remove_from_playlist">\"%1$s\" listesinden kaldır</string>
<string name="action_scroll_to_bottom">En alta kaydır</string>
<string name="favorite_list_add_stations">Favori ekle</string>
<string name="favorite_list_all">Tümü</string>
<string name="favorite_list_assign_title">Oynatma listelerine ekle</string>
<string name="favorite_list_create">Yeni oynatma listesi</string>
<string name="favorite_list_delete_message">\"%1$s\" silinsin mi? İstasyonlarınız Favoriler\'de kalacaktır.</string>
<string name="favorite_list_delete_title">Oynatma listesini sil</string>
<string name="favorite_list_edit_stations">İstasyonları düzenle</string>
<string name="favorite_list_empty">Bu oynatma listesi boş</string>
<string name="favorite_list_manage">Oynatma listelerini yönet</string>
<string name="favorite_list_name_hint">Oynatma listesi adı</string>
<string name="favorite_list_none_yet">Henüz oynatma listesi yok. Favorilerinizi düzenlemek için bir tane oluşturun.</string>
<string name="favorite_list_rename">Oynatma listesini yeniden adlandır</string>

[values-uk]
<string name="accessibility_filter_playlist">Фільтр плейлиста: %1$s</string>
<string name="accessibility_playlist_delete">Видалити %1$s</string>
<string name="accessibility_playlist_edit_stations">Редагувати станції в %1$s</string>
<string name="accessibility_playlist_rename">Перейменувати %1$s</string>
<string name="action_clear_playlist_filter">Очистити фільтр плейлиста</string>
<string name="action_manage_station_playlists">Додати до плейлиста</string>
<string name="action_remove_from_playlist">Видалити з «%1$s»</string>
<string name="action_scroll_to_bottom">Прокрутити вниз</string>
<string name="favorite_list_add_stations">Додати вибране</string>
<string name="favorite_list_all">Усі</string>
<string name="favorite_list_assign_title">Додати до плейлистів</string>
<string name="favorite_list_create">Новий плейлист</string>
<string name="favorite_list_delete_message">Видалити «%1$s»? Ваші станції залишаться у Вибраному.</string>
<string name="favorite_list_delete_title">Видалити плейлист</string>
<string name="favorite_list_edit_stations">Редагувати станції</string>
<string name="favorite_list_empty">Цей плейлист порожній</string>
<string name="favorite_list_manage">Керувати плейлистами</string>
<string name="favorite_list_name_hint">Назва плейлиста</string>
<string name="favorite_list_none_yet">Плейлистів ще немає. Створіть один, щоб упорядкувати вибране.</string>
<string name="favorite_list_rename">Перейменувати плейлист</string>

[values-ur]
<string name="accessibility_filter_playlist">پلے لسٹ فلٹر: %1$s</string>
<string name="accessibility_playlist_delete">%1$s حذف کریں</string>
<string name="accessibility_playlist_edit_stations">%1$s میں اسٹیشنز میں ترمیم کریں</string>
<string name="accessibility_playlist_rename">%1$s کا نام تبدیل کریں</string>
<string name="action_clear_playlist_filter">پلے لسٹ فلٹر صاف کریں</string>
<string name="action_manage_station_playlists">پلے لسٹ میں شامل کریں</string>
<string name="action_remove_from_playlist">\"%1$s\" سے ہٹائیں</string>
<string name="action_scroll_to_bottom">سب سے نیچے سکرول کریں</string>
<string name="favorite_list_add_stations">پسندیدہ شامل کریں</string>
<string name="favorite_list_all">تمام</string>
<string name="favorite_list_assign_title">پلے لسٹس میں شامل کریں</string>
<string name="favorite_list_create">نئی پلے لسٹ</string>
<string name="favorite_list_delete_message">\"%1$s\" حذف کریں؟ آپ کے اسٹیشنز پسندیدہ میں برقرار رہیں گے۔</string>
<string name="favorite_list_delete_title">پلے لسٹ حذف کریں</string>
<string name="favorite_list_edit_stations">اسٹیشنز میں ترمیم کریں</string>
<string name="favorite_list_empty">یہ پلے لسٹ خالی ہے</string>
<string name="favorite_list_manage">پلے لسٹس کا نظم کریں</string>
<string name="favorite_list_name_hint">پلے لسٹ کا نام</string>
<string name="favorite_list_none_yet">ابھی تک کوئی پلے لسٹ نہیں ہے۔ اپنے پسندیدہ اسٹیشنز ترتیب دینے کے لیے ایک بنائیں۔</string>
<string name="favorite_list_rename">پلے لسٹ کا نام تبدیل کریں</string>

[values-uz]
<string name="accessibility_filter_playlist">Pleylist filtri: %1$s</string>
<string name="accessibility_playlist_delete">%1$s ni o‘chirish</string>
<string name="accessibility_playlist_edit_stations">%1$s ichidagi stansiyalarni tahrirlash</string>
<string name="accessibility_playlist_rename">%1$s nomini o‘zgartirish</string>
<string name="action_clear_playlist_filter">Pleylist filtrini tozalash</string>
<string name="action_manage_station_playlists">Pleylistga qo‘shish</string>
<string name="action_remove_from_playlist">\"%1$s\" dan olib tashlash</string>
<string name="action_scroll_to_bottom">Eng pastga aylantirish</string>
<string name="favorite_list_add_stations">Sevimlilarni qo‘shish</string>
<string name="favorite_list_all">Barchasi</string>
<string name="favorite_list_assign_title">Pleylistlarga qo‘shish</string>
<string name="favorite_list_create">Yangi pleylist</string>
<string name="favorite_list_delete_message">\"%1$s\" o‘chirilsinmi? Stansiyalaringiz Sevimlilarda saqlanib qoladi.</string>
<string name="favorite_list_delete_title">Pleylistni o‘chirish</string>
<string name="favorite_list_edit_stations">Stansiyalarni tahrirlash</string>
<string name="favorite_list_empty">Bu pleylist bo‘sh</string>
<string name="favorite_list_manage">Pleylistlarni boshqarish</string>
<string name="favorite_list_name_hint">Pleylist nomi</string>
<string name="favorite_list_none_yet">Hali pleylistlar yo‘q. Sevimlilaringizni tartibga solish uchun bittasini yarating.</string>
<string name="favorite_list_rename">Pleylist nomini o‘zgartirish</string>

[values-vi]
<string name="accessibility_filter_playlist">Bộ lọc danh sách phát: %1$s</string>
<string name="accessibility_playlist_delete">Xóa %1$s</string>
<string name="accessibility_playlist_edit_stations">Chỉnh sửa đài trong %1$s</string>
<string name="accessibility_playlist_rename">Đổi tên %1$s</string>
<string name="action_clear_playlist_filter">Xóa bộ lọc danh sách phát</string>
<string name="action_manage_station_playlists">Thêm vào danh sách phát</string>
<string name="action_remove_from_playlist">Xóa khỏi \"%1$s\"</string>
<string name="action_scroll_to_bottom">Cuộn xuống dưới cùng</string>
<string name="favorite_list_add_stations">Thêm mục yêu thích</string>
<string name="favorite_list_all">Tất cả</string>
<string name="favorite_list_assign_title">Thêm vào danh sách phát</string>
<string name="favorite_list_create">Danh sách phát mới</string>
<string name="favorite_list_delete_message">Xóa \"%1$s\"? Các đài của bạn sẽ vẫn nằm trong Mục yêu thích.</string>
<string name="favorite_list_delete_title">Xóa danh sách phát</string>
<string name="favorite_list_edit_stations">Chỉnh sửa đài</string>
<string name="favorite_list_empty">Danh sách phát này trống</string>
<string name="favorite_list_manage">Quản lý danh sách phát</string>
<string name="favorite_list_name_hint">Tên danh sách phát</string>
<string name="favorite_list_none_yet">Chưa có danh sách phát nào. Hãy tạo một danh sách để sắp xếp các mục yêu thích của bạn.</string>
<string name="favorite_list_rename">Đổi tên danh sách phát</string>

[values-xh]
<string name="accessibility_filter_playlist">Isihluzi soluhlu lokudlala: %1$s</string>
<string name="accessibility_playlist_delete">Cima %1$s</string>
<string name="accessibility_playlist_edit_stations">Hlela izitishi kwi-%1$s</string>
<string name="accessibility_playlist_rename">Thiya ngokutsha %1$s</string>
<string name="action_clear_playlist_filter">Susa isihluzi soluhlu lokudlala</string>
<string name="action_manage_station_playlists">Yongeza kuluhlu lokudlala</string>
<string name="action_remove_from_playlist">Susa kwi-\"%1$s\"</string>
<string name="action_scroll_to_bottom">Skrolela ezantsi</string>
<string name="favorite_list_add_stations">Yongeza izintandokazi</string>
<string name="favorite_list_all">Zonke</string>
<string name="favorite_list_assign_title">Yongeza kwizintlu zokudlala</string>
<string name="favorite_list_create">Uluhlu olutsha lokudlala</string>
<string name="favorite_list_delete_message">Cima \"%1$s\"? Izitishi zakho ziya kuhlala kwizintandokazi.</string>
<string name="favorite_list_delete_title">Cima uluhlu lokudlala</string>
<string name="favorite_list_edit_stations">Hlela izitishi</string>
<string name="favorite_list_empty">Olu luhlu lokudlala alunanto</string>
<string name="favorite_list_manage">Lawula izintlu zokudlala</string>
<string name="favorite_list_name_hint">Igama loluhlu lokudlala</string>
<string name="favorite_list_none_yet">Akukho zintlu zokudlala okwangoku. Yenza enye ukuze ulungelelanise izintandokazi zakho.</string>
<string name="favorite_list_rename">Thiya ngokutsha uluhlu lokudlala</string>

[values-yi]
<string name="accessibility_filter_playlist">פּלייליסט פֿילטער: %1$s</string>
<string name="accessibility_playlist_delete">אויסמעקן %1$s</string>
<string name="accessibility_playlist_edit_stations">רעדאַגירן סטאַנציעס אין %1$s</string>
<string name="accessibility_playlist_rename">ענדערן נאָמען פֿון %1$s</string>
<string name="action_clear_playlist_filter">אויסמעקן פּלייליסט פֿילטער</string>
<string name="action_manage_station_playlists">צוגעבן צו פּלייליסט</string>
<string name="action_remove_from_playlist">אַראָפּנעמען פֿון \"%1$s\"</string>
<string name="action_scroll_to_bottom">סקראָלן צום דנאָ</string>
<string name="favorite_list_add_stations">צוגעבן פֿאַוואָריטן</string>
<string name="favorite_list_all">אַלע</string>
<string name="favorite_list_assign_title">צוגעבן צו פּלייליסטן</string>
<string name="favorite_list_create">נײַע פּלייליסט</string>
<string name="favorite_list_delete_message">אויסמעקן \"%1$s\"? אײַערע סטאַנציעס וועלן בלײַבן אין פֿאַוואָריטן.</string>
<string name="favorite_list_delete_title">אויסמעקן פּלייליסט</string>
<string name="favorite_list_edit_stations">רעדאַגירן סטאַנציעס</string>
<string name="favorite_list_empty">די פּלייליסט איז ליידיק</string>
<string name="favorite_list_manage">פֿאַרוואַלטן פּלייליסטן</string>
<string name="favorite_list_name_hint">פּלייליסט נאָמען</string>
<string name="favorite_list_none_yet">נאָך נישטאָ קיין פּלייליסטן. שאַפֿט איינע צו אָרגאַניזירן אײַערע פֿאַוואָריטן.</string>
<string name="favorite_list_rename">ענדערן פּלייליסט נאָמען</string>

[values-yo]
<string name="accessibility_filter_playlist">Àlẹ̀mọ́ àkójọ orin: %1$s</string>
<string name="accessibility_playlist_delete">Pa %1$s rẹ́</string>
<string name="accessibility_playlist_edit_stations">Ṣàtúnṣe àwọn ibùdó nínú %1$s</string>
<string name="accessibility_playlist_rename">Yí orúkọ %1$s padà</string>
<string name="action_clear_playlist_filter">Pa àlẹ̀mọ́ àkójọ orin rẹ́</string>
<string name="action_manage_station_playlists">Fi kún àkójọ orin</string>
<string name="action_remove_from_playlist">Yọ kúrò nínú \"%1$s\"</string>
<string name="action_scroll_to_bottom">Yí lọ sí ìsàlẹ̀ pátápátá</string>
<string name="favorite_list_add_stations">Fi àwọn àyànfẹ́ kún</string>
<string name="favorite_list_all">Gbogbo</string>
<string name="favorite_list_assign_title">Fi kún àwọn àkójọ orin</string>
<string name="favorite_list_create">Àkójọ orin tuntun</string>
<string name="favorite_list_delete_message">Pa \"%1$s\" rẹ́? Àwọn ibùdó rẹ yóò wà nínú Àwọn àyànfẹ́.</string>
<string name="favorite_list_delete_title">Pa àkójọ orin rẹ́</string>
<string name="favorite_list_edit_stations">Ṣàtúnṣe àwọn ibùdó</string>
<string name="favorite_list_empty">Àkójọ orin yìí ṣófo</string>
<string name="favorite_list_manage">Ṣàkóso àwọn àkójọ orin</string>
<string name="favorite_list_name_hint">Orúkọ àkójọ orin</string>
<string name="favorite_list_none_yet">Kò sí àkójọ orin kankan síbẹ̀. Dá ọ̀kan sílẹ̀ láti ṣètò àwọn àyànfẹ́ rẹ.</string>
<string name="favorite_list_rename">Yí orúkọ àkójọ orin padà</string>

[values-zh-rCN]
<string name="accessibility_filter_playlist">播放列表筛选：%1$s</string>
<string name="accessibility_playlist_delete">删除 %1$s</string>
<string name="accessibility_playlist_edit_stations">编辑 %1$s 中的电台</string>
<string name="accessibility_playlist_rename">重命名 %1$s</string>
<string name="action_clear_playlist_filter">清除播放列表筛选</string>
<string name="action_manage_station_playlists">添加到播放列表</string>
<string name="action_remove_from_playlist">从“%1$s”中移除</string>
<string name="action_scroll_to_bottom">滚动到底部</string>
<string name="favorite_list_add_stations">添加收藏</string>
<string name="favorite_list_all">全部</string>
<string name="favorite_list_assign_title">添加到播放列表</string>
<string name="favorite_list_create">新建播放列表</string>
<string name="favorite_list_delete_message">删除“%1$s”？您的电台仍会保留在收藏夹中。</string>
<string name="favorite_list_delete_title">删除播放列表</string>
<string name="favorite_list_edit_stations">编辑电台</string>
<string name="favorite_list_empty">此播放列表为空</string>
<string name="favorite_list_manage">管理播放列表</string>
<string name="favorite_list_name_hint">播放列表名称</string>
<string name="favorite_list_none_yet">暂无播放列表。创建一个来整理您的收藏。</string>
<string name="favorite_list_rename">重命名播放列表</string>

[values-zh-rHK]
<string name="accessibility_filter_playlist">播放清單篩選：%1$s</string>
<string name="accessibility_playlist_delete">刪除 %1$s</string>
<string name="accessibility_playlist_edit_stations">編輯 %1$s 中的電台</string>
<string name="accessibility_playlist_rename">重新命名 %1$s</string>
<string name="action_clear_playlist_filter">清除播放清單篩選</string>
<string name="action_manage_station_playlists">新增至播放清單</string>
<string name="action_remove_from_playlist">從「%1$s」移除</string>
<string name="action_scroll_to_bottom">捲動到底部</string>
<string name="favorite_list_add_stations">新增最愛</string>
<string name="favorite_list_all">全部</string>
<string name="favorite_list_assign_title">新增至播放清單</string>
<string name="favorite_list_create">新增播放清單</string>
<string name="favorite_list_delete_message">刪除「%1$s」？您的電台仍會保留在最愛中。</string>
<string name="favorite_list_delete_title">刪除播放清單</string>
<string name="favorite_list_edit_stations">編輯電台</string>
<string name="favorite_list_empty">此播放清單為空</string>
<string name="favorite_list_manage">管理播放清單</string>
<string name="favorite_list_name_hint">播放清單名稱</string>
<string name="favorite_list_none_yet">尚無播放清單。建立一個來整理您的最愛。</string>
<string name="favorite_list_rename">重新命名播放清單</string>

[values-zh-rTW]
<string name="accessibility_filter_playlist">播放清單篩選：%1$s</string>
<string name="accessibility_playlist_delete">刪除 %1$s</string>
<string name="accessibility_playlist_edit_stations">編輯 %1$s 中的電台</string>
<string name="accessibility_playlist_rename">重新命名 %1$s</string>
<string name="action_clear_playlist_filter">清除播放清單篩選</string>
<string name="action_manage_station_playlists">新增至播放清單</string>
<string name="action_remove_from_playlist">從「%1$s」移除</string>
<string name="action_scroll_to_bottom">捲動到底部</string>
<string name="favorite_list_add_stations">新增最愛</string>
<string name="favorite_list_all">全部</string>
<string name="favorite_list_assign_title">新增至播放清單</string>
<string name="favorite_list_create">新增播放清單</string>
<string name="favorite_list_delete_message">刪除「%1$s」？您的電台仍會保留在最愛中。</string>
<string name="favorite_list_delete_title">刪除播放清單</string>
<string name="favorite_list_edit_stations">編輯電台</string>
<string name="favorite_list_empty">此播放清單為空</string>
<string name="favorite_list_manage">管理播放清單</string>
<string name="favorite_list_name_hint">播放清單名稱</string>
<string name="favorite_list_none_yet">尚無播放清單。建立一個來整理您的最愛。</string>
<string name="favorite_list_rename">重新命名播放清單</string>

[values-zu]
<string name="accessibility_filter_playlist">Isihlungi sohlu lwadlalwayo: %1$s</string>
<string name="accessibility_playlist_delete">Susa %1$s</string>
<string name="accessibility_playlist_edit_stations">Hlela iziteshi ku-%1$s</string>
<string name="accessibility_playlist_rename">Qamba kabusha %1$s</string>
<string name="action_clear_playlist_filter">Sula isihlungi sohlu lwadlalwayo</string>
<string name="action_manage_station_playlists">Engeza ohlwini lwadlalwayo</string>
<string name="action_remove_from_playlist">Susa ku-\"%1$s\"</string>
<string name="action_scroll_to_bottom">Skrolela phansi</string>
<string name="favorite_list_add_stations">Engeza izintandokazi</string>
<string name="favorite_list_all">Zonke</string>
<string name="favorite_list_assign_title">Engeza ezinhlwini zokudlalwayo</string>
<string name="favorite_list_create">Uhlu olusha lwadlalwayo</string>
<string name="favorite_list_delete_message">Susa \"%1$s\"? Iziteshi zakho zizohlala ezintandokazini.</string>
<string name="favorite_list_delete_title">Susa uhlu lwadlalwayo</string>
<string name="favorite_list_edit_stations">Hlela iziteshi</string>
<string name="favorite_list_empty">Lolu hlu lwadlalwayo alunalutho</string>
<string name="favorite_list_manage">Phatha izinhla zokudlalwayo</string>
<string name="favorite_list_name_hint">Igama lohlu lwadlalwayo</string>
<string name="favorite_list_none_yet">Azikho izinhla zokudlalwayo okwamanje. Dala eyodwa ukuze uhlele izintandokazi zakho.</string>
<string name="favorite_list_rename">Qamba kabusha uhlu lwadlalwayo</string>
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