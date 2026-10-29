# Monuments

The mod builds these monuments where they really stand, as the land under them is first generated.
To skip one, put its id in `monuments.disabled` in the config; to skip a whole country, put its
two-letter code in `monuments.disabledCountries`; to skip them all, set `monuments.enabled` to false.
See [Configuration](Configuration.md).

A monument stands on a stone footing on the first free block above the ground or water at its
centre; the trees and buildings in its way are cleared. Wrecks lie on the sea bed. Chunks that
already exist are never changed, so switching a monument on after its land was explored does not
build it, and switching it off does not remove it.

## North America

| Id | Monument | Country |
|---|---|---|
| `white_house` | White House | United States |
| `statue_of_liberty` | Statue of Liberty | United States |
| `hollywood_sign` | Hollywood Sign | United States |
| `golden_gate_bridge` | Golden Gate Bridge | United States |
| `mount_rushmore` | Mount Rushmore | United States |
| `gateway_arch` | Gateway Arch | United States |
| `space_needle` | Space Needle | United States |
| `kennedy_space_center` | Kennedy Space Center | United States |
| `cn_tower` | CN Tower | Canada |
| `parliament_hill` | Parliament Hill | Canada |
| `chichen_itza` | Chichen Itza | Mexico |
| `angel_of_independence` | Angel of Independence | Mexico |
| `tikal` | Tikal | Guatemala |
| `monumento_al_divino_salvador_del_mundo` | Monumento al Divino Salvador del Mundo | El Salvador |
| `copan` | Copan | Honduras |
| `old_cathedral_of_managua` | Old Cathedral of Managua | Nicaragua |
| `bridge_of_the_americas` | Bridge of the Americas | Panama |
| `el_capitolio` | El Capitolio | Cuba |
| `citadelle_laferriere` | Citadelle Laferriere | Haiti |
| `columbus_lighthouse` | Columbus Lighthouse | Dominican Republic |
| `el_morro` | El Morro | Puerto Rico |

## South America

| Id | Monument | Country |
|---|---|---|
| `christ_the_redeemer` | Christ the Redeemer | Brazil |
| `national_congress_of_brazil` | National Congress of Brazil | Brazil |
| `amazon_theatre` | Amazon Theatre | Brazil |
| `obelisco_de_buenos_aires` | Obelisco de Buenos Aires | Argentina |
| `les_eclaireurs_lighthouse` | Les Eclaireurs Lighthouse | Argentina |
| `easter_island_moai` | Easter Island Moai | Chile |
| `gran_torre_santiago` | Gran Torre Santiago | Chile |
| `machu_picchu` | Machu Picchu | Peru |
| `gate_of_the_sun` | Gate of the Sun | Bolivia |
| `mitad_del_mundo` | Mitad del Mundo | Ecuador |
| `las_lajas_sanctuary` | Las Lajas Sanctuary | Colombia |
| `saint_peter_and_paul_cathedral` | Saint Peter and Paul Cathedral | Suriname |
| `guiana_space_centre` | Guiana Space Centre | French Guiana |
| `palacio_de_los_lopez` | Palacio de los Lopez | Paraguay |
| `palacio_salvo` | Palacio Salvo | Uruguay |

## Europe

| Id | Monument | Country |
|---|---|---|
| `eiffel_tower` | Eiffel Tower | France |
| `mont_saint_michel` | Mont-Saint-Michel | France |
| `big_ben` | Big Ben | United Kingdom |
| `stonehenge` | Stonehenge | United Kingdom |
| `edinburgh_castle` | Edinburgh Castle | United Kingdom |
| `spire_of_dublin` | Spire of Dublin | Ireland |
| `peace_palace` | Peace Palace | Netherlands |
| `atomium` | Atomium | Belgium |
| `vianden_castle` | Vianden Castle | Luxembourg |
| `brandenburg_gate` | Brandenburg Gate | Germany |
| `neuschwanstein_castle` | Neuschwanstein Castle | Germany |
| `cologne_cathedral` | Cologne Cathedral | Germany |
| `zytglogge` | Zytglogge | Switzerland |
| `wiener_riesenrad` | Wiener Riesenrad | Austria |
| `colosseum` | Colosseum | Italy |
| `leaning_tower_of_pisa` | Leaning Tower of Pisa | Italy |
| `sagrada_familia` | Sagrada Familia | Spain |
| `alhambra` | Alhambra | Spain |
| `belem_tower` | Belem Tower | Portugal |
| `parthenon` | Parthenon | Greece |
| `hallgrimskirkja` | Hallgrimskirkja | Iceland |
| `borgund_stave_church` | Borgund Stave Church | Norway |
| `stockholm_city_hall` | Stockholm City Hall | Sweden |
| `helsinki_cathedral` | Helsinki Cathedral | Finland |
| `little_mermaid` | Little Mermaid | Denmark |
| `toompea_castle` | Toompea Castle | Estonia |
| `freedom_monument` | Freedom Monument | Latvia |
| `gediminas_tower` | Gediminas Tower | Lithuania |
| `palace_of_culture_and_science` | Palace of Culture and Science | Poland |
| `wawel_castle` | Wawel Castle | Poland |
| `st_vitus_cathedral` | St. Vitus Cathedral | Czechia |
| `spis_castle` | Spis Castle | Slovakia |
| `hungarian_parliament` | Hungarian Parliament | Hungary |
| `bled_castle` | Bled Castle | Slovenia |
| `pula_arena` | Pula Arena | Croatia |
| `stari_most` | Stari Most | Bosnia and Herzegovina |
| `church_of_saint_sava` | Church of Saint Sava | Serbia |
| `kotor_fortress` | Kotor Fortress | Montenegro |
| `newborn_monument` | Newborn Monument | Kosovo |
| `skanderbeg_monument` | Skanderbeg Monument | Albania |
| `warrior_on_a_horse` | Warrior on a Horse | North Macedonia |
| `alexander_nevsky_cathedral` | Alexander Nevsky Cathedral | Bulgaria |
| `palace_of_the_parliament` | Palace of the Parliament | Romania |
| `triumphal_arch_of_chisinau` | Triumphal Arch of Chisinau | Moldova |
| `motherland_monument` | Motherland Monument | Ukraine |
| `chernobyl_shelter` | Chernobyl Shelter | Ukraine |
| `national_library_of_belarus` | National Library of Belarus | Belarus |
| `kremlin_and_st_basil_s_cathedral` | Kremlin and St. Basil's Cathedral | Russia |
| `church_of_the_savior_on_blood` | Church of the Savior on Blood | Russia |
| `the_motherland_calls` | The Motherland Calls | Russia |
| `hagia_sophia` | Hagia Sophia | Turkey |
| `kyrenia_castle` | Kyrenia Castle | Cyprus |

## Africa

| Id | Monument | Country |
|---|---|---|
| `pyramids_of_giza_and_the_sphinx` | Pyramids of Giza and the Sphinx | Egypt |
| `abu_simbel` | Abu Simbel | Egypt |
| `lighthouse_of_alexandria` | Lighthouse of Alexandria | Egypt |
| `leptis_magna` | Leptis Magna | Libya |
| `amphitheatre_of_el_jem` | Amphitheatre of El Jem | Tunisia |
| `maqam_echahid` | Maqam Echahid | Algeria |
| `hassan_ii_mosque` | Hassan II Mosque | Morocco |
| `african_renaissance_monument` | African Renaissance Monument | Senegal |
| `arch_22` | Arch 22 | Gambia |
| `presidential_palace_of_bissau` | Presidential Palace of Bissau | Guinea-Bissau |
| `grand_mosque_of_conakry` | Grand Mosque of Conakry | Guinea |
| `capitol_building_of_liberia` | Capitol Building of Liberia | Liberia |
| `basilica_of_our_lady_of_peace` | Basilica of Our Lady of Peace | Ivory Coast |
| `great_mosque_of_djenne` | Great Mosque of Djenne | Mali |
| `monument_of_national_heroes` | Monument of National Heroes | Burkina Faso |
| `black_star_gate` | Black Star Gate | Ghana |
| `independence_monument_of_lome` | Independence Monument of Lome | Togo |
| `amazon_statue` | Amazon Statue | Benin |
| `abuja_national_mosque` | Abuja National Mosque | Nigeria |
| `agadez_mosque` | Agadez Mosque | Niger |
| `place_de_la_nation` | Place de la Nation | Chad |
| `reunification_monument` | Reunification Monument | Cameroon |
| `notre_dame_cathedral_of_bangui` | Notre-Dame Cathedral of Bangui | Central African Republic |
| `pyramids_of_meroe` | Pyramids of Meroe | Sudan |
| `john_garang_mausoleum` | John Garang Mausoleum | South Sudan |
| `obelisk_of_axum` | Obelisk of Axum | Ethiopia |
| `asmara_cathedral` | Asmara Cathedral | Eritrea |
| `mogadishu_lighthouse` | Mogadishu Lighthouse | Somalia |
| `kenyatta_international_convention_centre` | Kenyatta International Convention Centre | Kenya |
| `independence_monument_of_uganda` | Independence Monument of Uganda | Uganda |
| `kigali_convention_centre` | Kigali Convention Centre | Rwanda |
| `unity_monument` | Unity Monument | Burundi |
| `limete_tower` | Limete Tower | DR Congo |
| `pointe_noire_lighthouse` | Pointe-Noire Lighthouse | Republic of the Congo |
| `agostinho_neto_mausoleum` | Agostinho Neto Mausoleum | Angola |
| `freedom_statue` | Freedom Statue | Zambia |
| `great_zimbabwe` | Great Zimbabwe | Zimbabwe |
| `maputo_railway_station` | Maputo Railway Station | Mozambique |
| `three_dikgosi_monument` | Three Dikgosi Monument | Botswana |
| `christuskirche` | Christuskirche | Namibia |
| `table_mountain_cableway` | Table Mountain Cableway | South Africa |
| `voortrekker_monument` | Voortrekker Monument | South Africa |
| `avenue_of_the_baobabs` | Avenue of the Baobabs | Madagascar |

## Asia

| Id | Monument | Country |
|---|---|---|
| `russky_bridge` | Russky Bridge | Russia |
| `mother_of_georgia` | Mother of Georgia | Georgia |
| `mother_armenia` | Mother Armenia | Armenia |
| `flame_towers` | Flame Towers | Azerbaijan |
| `dome_of_the_rock` | Dome of the Rock | Israel and Palestine |
| `baalbek` | Baalbek | Lebanon |
| `citadel_of_aleppo` | Citadel of Aleppo | Syria |
| `petra_treasury` | Petra Treasury | Jordan |
| `kingdom_centre` | Kingdom Centre | Saudi Arabia |
| `dar_al_hajar` | Dar al-Hajar | Yemen |
| `sultan_qaboos_grand_mosque` | Sultan Qaboos Grand Mosque | Oman |
| `burj_khalifa` | Burj Khalifa | United Arab Emirates |
| `sheikh_zayed_grand_mosque` | Sheikh Zayed Grand Mosque | United Arab Emirates |
| `doha_tower` | Doha Tower | Qatar |
| `bahrain_world_trade_center` | Bahrain World Trade Center | Bahrain |
| `kuwait_towers` | Kuwait Towers | Kuwait |
| `ziggurat_of_ur` | Ziggurat of Ur | Iraq |
| `malwiya_minaret` | Malwiya Minaret | Iraq |
| `azadi_tower` | Azadi Tower | Iran |
| `bayterek_tower` | Bayterek Tower | Kazakhstan |
| `semipalatinsk_test_site` | Semipalatinsk Test Site: the blast crater at the first Soviet atomic bomb's ground zero, with eight "geese" towers and six bunkers round it (`semipalatinsk_test_site_1` to `_14`; switching off the site switches them off too) | Kazakhstan |
| `atomic_lake` | Atomic Lake (the Chagan crater, flooded) | Kazakhstan |
| `registan` | Registan | Uzbekistan |
| `neutrality_monument` | Neutrality Monument | Turkmenistan |
| `burana_tower` | Burana Tower | Kyrgyzstan |
| `dushanbe_flagpole` | Dushanbe Flagpole | Tajikistan |
| `minaret_of_jam` | Minaret of Jam | Afghanistan |
| `minar_e_pakistan` | Minar-e-Pakistan | Pakistan |
| `taj_mahal` | Taj Mahal | India |
| `india_gate` | India Gate | India |
| `gateway_of_india` | Gateway of India | India |
| `boudhanath` | Boudhanath | Nepal |
| `tiger_s_nest` | Tiger's Nest | Bhutan |
| `ahsan_manzil` | Ahsan Manzil | Bangladesh |
| `lotus_tower` | Lotus Tower | Sri Lanka |
| `shwedagon_pagoda` | Shwedagon Pagoda | Myanmar |
| `golden_rock` | Golden Rock | Myanmar |
| `wat_arun` | Wat Arun | Thailand |
| `pha_that_luang` | Pha That Luang | Laos |
| `angkor_wat` | Angkor Wat | Cambodia |
| `ho_chi_minh_mausoleum` | Ho Chi Minh Mausoleum | Vietnam |
| `petronas_towers` | Petronas Towers | Malaysia |
| `marina_bay_sands` | Marina Bay Sands | Singapore |
| `omar_ali_saifuddien_mosque` | Omar Ali Saifuddien Mosque | Brunei |
| `monas` | Monas | Indonesia |
| `borobudur` | Borobudur | Indonesia |
| `rizal_monument` | Rizal Monument | Philippines |
| `cristo_rei_of_dili` | Cristo Rei of Dili | Timor-Leste |
| `great_wall_of_china` | Great Wall of China | China |
| `forbidden_city` | Forbidden City | China |
| `oriental_pearl_tower` | Oriental Pearl Tower | China |
| `giant_wild_goose_pagoda` | Giant Wild Goose Pagoda | China |
| `potala_palace` | Potala Palace | China |
| `leshan_giant_buddha` | Leshan Giant Buddha | China |
| `international_finance_centre` | International Finance Centre | Hong Kong |
| `taipei_101` | Taipei 101 | Taiwan |
| `genghis_khan_equestrian_statue` | Genghis Khan Equestrian Statue | Mongolia |
| `juche_tower` | Juche Tower | North Korea |
| `n_seoul_tower` | N Seoul Tower | South Korea |
| `tokyo_tower` | Tokyo Tower | Japan |
| `himeji_castle` | Himeji Castle | Japan |
| `itsukushima_torii` | Itsukushima Torii | Japan |

## Oceania

| Id | Monument | Country |
|---|---|---|
| `sydney_opera_house` | Sydney Opera House | Australia |
| `uluru` | Uluru | Australia |
| `sky_tower` | Sky Tower | New Zealand |
| `national_parliament_house` | National Parliament House | Papua New Guinea |

## Oddities, oceans and the poles

| Id | Monument | Country |
|---|---|---|
| `null_island` | Null Island | International waters |
| `north_pole` | North Pole | Arctic |
| `south_pole_station` | South Pole Station | Antarctica |
| `area_51` | Area 51 | United States |
| `bermuda_triangle` | Bermuda Triangle | International waters |
| `rms_titanic` | RMS Titanic | International waters |
| `rms_lusitania` | RMS Lusitania | Ireland |
| `hmhs_britannic` | HMHS Britannic | Greece |
| `rms_empress_of_ireland` | RMS Empress of Ireland | Canada |
| `ss_andrea_doria` | SS Andrea Doria | United States |
| `rms_republic` | RMS Republic | United States |
| `rms_carpathia` | RMS Carpathia | Ireland |
| `mv_wilhelm_gustloff` | MV Wilhelm Gustloff | Poland |
| `ms_estonia` | MS Estonia | Finland |
| `mv_dona_paz` | MV Dona Paz | Philippines |
| `ss_president_coolidge` | SS President Coolidge | Vanuatu |
| `ms_mikhail_lermontov` | MS Mikhail Lermontov | New Zealand |
| `ss_yongala` | SS Yongala | Australia |
| `ss_central_america` | SS Central America | United States |
| `ss_edmund_fitzgerald` | SS Edmund Fitzgerald | Canada |
| `ss_carl_d_bradley` | SS Carl D. Bradley | United States |
| `ss_thistlegorm` | SS Thistlegorm | Egypt |
| `fujikawa_maru` | Fujikawa Maru | Micronesia |
| `rainbow_warrior` | Rainbow Warrior | New Zealand |
| `bismarck` | Bismarck | International waters |
| `hms_hood` | HMS Hood | International waters |
| `yamato` | Yamato | Japan |
| `musashi` | Musashi | Philippines |
| `uss_arizona` | USS Arizona | United States |
| `uss_indianapolis` | USS Indianapolis | International waters |
| `hms_prince_of_wales` | HMS Prince of Wales | Malaysia |
| `scharnhorst` | Scharnhorst | Norway |
| `admiral_graf_spee` | Admiral Graf Spee | Uruguay |
| `ara_general_belgrano` | ARA General Belgrano | Argentina |
| `hmas_sydney` | HMAS Sydney | Australia |
| `sms_markgraf` | SMS Markgraf | United Kingdom |
| `hms_victoria` | HMS Victoria | Lebanon |
| `uss_monitor` | USS Monitor | United States |
| `uss_yorktown` | USS Yorktown | International waters |
| `uss_saratoga` | USS Saratoga | Marshall Islands |
| `uss_thresher` | USS Thresher | International waters |
| `uss_scorpion` | USS Scorpion | International waters |
| `k_278_komsomolets` | K-278 Komsomolets | International waters |
| `endurance` | Endurance | Antarctica |
| `hms_erebus` | HMS Erebus | Canada |
| `hms_terror` | HMS Terror | Canada |
| `batavia` | Batavia | Australia |
| `san_jose` | San Jose | Colombia |
| `nuestra_senora_de_atocha` | Nuestra Senora de Atocha | United States |
| `queen_anne_s_revenge` | Queen Anne's Revenge | United States |
| `whydah_gally` | Whydah Gally | United States |
| `antikythera_wreck` | Antikythera Wreck | Greece |

## Your own monuments

`monuments.extraFiles` takes paths to more monument files in the same format as the built-in one
(`src/main/resources/earth_worldgen/monuments.bin.gz`): gzipped, big-endian, starting with "EMON" and
version 1, then a palette of block ids and, for each monument, its id, name, country code, latitude,
longitude, block position at 1:5, a flag for wrecks, the air to clear and its blocks as filled boxes
relative to the block on the ground at its centre. The full layout is described at the top of
`Monuments.java`.
