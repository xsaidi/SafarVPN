# SafarVPN — handoff следующему чату, 15.10.2026

## Текущее задание завершено

Пользователь попросил сохранить код Android 1.4.9 в GitHub и подготовить handoff. APK загружать не требуется. Новых UI-задач пока нет: дождаться следующего задания пользователя.

Checkout: `C:\Users\saidix\Desktop\safarvpn\safarvpn-android`, ветка `master`.
Пользовательский remote: **safarvpn**, `https://github.com/xsaidi/SafarVPN`.
`origin` — upstream SpaceNeuroX/proxy-turn-vk-android; пользовательские изменения туда не пушить.
Снимок кода: тег **safarvpn-1.4.9**, версия **1.4.9 / code47**.
Точный commit после push записан в workspace-копии этого handoff. Проверять remote refs перед продолжением.
Версии 1.4.7–1.4.9 подготовлены в этом чате поверх c429983 (1.4.6); отдельных коммитов/тегов 1.4.7 и 1.4.8 не создавали.

## Что изменено

- Режим приложения по умолчанию «Пользователь». Сохранённый выбор режима не сбрасывается.
- Вкладка «Туннель»: круглая кнопка, затем список серверов с подписками; «Расширенные настройки» в самом низу общего прокручиваемого списка. Выпадающий выбор сервера убран.
- В Advanced перенесены WG/Raw/SOCKS5, секреты, адрес сервера, VK-хеши, мощность, вход VK и режим VK. Вход VK для TURN не путать с аккаунтом SafarVPN через Google/Telegram.
- Круглая кнопка: «Подключиться», «Подключение...», «Подключено». `TunnelManager.running` означает запуск процесса, а не готовность VPN. Новый `connectionReady` устанавливается после успешного запуска WireGuard/RAW либо готовности SOCKS-listener. Статистика воркеров и optional pipeline этот сигнал не устанавливают. При остановке/переподключении готовность сбрасывается.
- Пауза после отключения сокращена с 5 до 1 секунды. Реальный teardown по-прежнему ожидается существующим start/stop gate.
- Выбор сервера применяет существующий профиль. Повторное нажатие на выбранный сервер ничего не делает. Во время выбора/подключения/переподключения повторный выбор блокируется. При смене сервера с активным VPN выполняется переподключение.
- Вкладка «Аккаунт» заменила бывшую вкладку профилей/серверов. Туда перенесены данные аккаунта, статус подписки, Google/Telegram login, refresh и logout. Logout требует подтверждения, затем вызывает существующий защищённый AccountRepository.logout.
- Папки/фильтры/перемещение/экспорт профилей убраны из UI. Внутренние groupId и account namespace сохранены; миграции с удалением данных нет. Импорт нескольких конфигураций создаёт новые ручные профили, не заменяет существующие группы. QR-генератор админки вынесен в QrCode.kt.
- В последней 1.4.9 карточки серверов показывают название, устройства и пинг. Адрес IP/домен, кнопка редактирования, детали срока/ошибок и действие отвязки устройства в карточке не показываются.
- Свайп-удаление серверов, подтверждение такого удаления и подсказка свайпа удалены. Долгое перетаскивание для порядка серверов осталось.
- Заголовок «Сервера» над подписками и весь его toolbar (справка, общий ping, сортировка, more/ZIP) удалён.
- Общий ping **рядом с обновлением подписки** сохранён: существующий PingHelper, прогресс/отмена и защита от дублей. Индивидуальный ping в карточках сохранён.
- В карточку подписки добавлена кнопка «Поддержка», открывающая `https://t.me/xsaidi` через Android URI handler.
- В PingHelper убрано логирование хешей, auth-команды и сырых строк дочернего процесса.

## Файлы для продолжения

- `app/src/main/java/com/wdtt/client/MainActivity.kt`: навигация, импорт файлов теперь открывает Туннель.
- `ui/settings/SettingsTab.kt`: настройки и кнопка через header/footer slots ProfilesTab.
- `ui/settings/TunnelConnectButton.kt`: круглая Compose-кнопка.
- `ui/profiles/ProfilesTab.kt`: единый список серверов, подписки, выбор/импорт/ping, header/footer.
- `ui/profiles/SubscriptionsSheet.kt`: подписка, refresh/ping/support.
- `account/AccountTab.kt`, `account/AccountUi.kt`: аккаунт и подтверждение logout.
- `tunnel/TunnelManager.kt`: connectionReady, жизненный цикл транспорта; не заменять running новым флагом во всём сервисе без анализа.
- `settings/SettingsStore.kt`: default User.
- `network/PingHelper.kt`, `ui/profiles/QrCode.kt`.
- `RELEASE-1.4.7.md`, `RELEASE-1.4.8.md`, `RELEASE-1.4.9.md`.

## APK и проверки

Локальный подписанный APK: `safarvpn-android/artifacts/SafarVPN-1.4.9-universal-release.apk`.
Размер 38,540,367 bytes; SHA256 `1254a5674330040bd38edbd73535c8c3d54623f99b0a056adf6f75bb57a76f15`.
applicationId `ru.safarvpn.com`, namespace `com.wdtt.client`.
Исходный сертификат SHA256 `8D16AE71E2423D5D599060E1D32C18CE52BB233C29732940313AEE4BE4F2CE68`.
Ключ остаётся вне Git в `safarvpn-signing/safarvpn-release.p12`; новый не создавать, секреты не читать/печатать.
Сборка release/R8 успешна, 11 unit-тестов (AccountProfilesTest 5, MobileAuthProtocolTest 6) без failures/errors. `git diff --check` пройден.
Проверены версия/code/package/исходный сертификат. Evidence: `artifacts/SafarVPN-1.4.9-verification.json`.
Это существующие тесты аккаунта/протокола; UI/E2E покрытие не добавляли. Phone/visual QA, реальный VPN, переход поддержки и logout на устройстве не проверены. Не считать сборку подтверждением phone QA.
Пользователь переходил к следующим правкам после APK 1.4.7/1.4.8; явного подтверждения «1.4.9 работает» пока нет.
ADB не использовался. **Без нового разрешения ADB не использовать; субагентов без нового явного запроса не создавать.**
APK/artifacts игнорируются Git. Код сохраняется в ветке/теге; новый GitHub Release и APK asset для 1.4.9 не создаются по текущему запросу.
Старый draft release 1.4.6 с неудачной загрузкой asset из предыдущего handoff не трогали.

Сборка Windows из checkout: `& .\scripts\build-release-windows.ps1 -RunTests`.
Проверка текущего APK: `& .\scripts\verify-ui-release.ps1` (фиксирована на 1.4.9/code47; адаптировать для будущей версии).
`verify-account-release.ps1` остаётся исторической проверкой 1.4.6/code44.
Если песочница блокирует NDK clang, использовать штатное require_escalated, не менять toolchain.
Новый релиз требует нового versionCode и отдельного APK; предыдущие APK в artifacts сохранены.

## Аккаунт и production

Backend в этом UI-чате не изменялся. Сохранены AccountRepository generation guards, безопасная очистка account-owned профилей и ProfilesStore.
Ручные профили/ручные подписки сохранять при logout/смене аккаунта. Сетевой сбой не должен удалять валидный account cache. Google/Telegram аккаунты автоматически не объединять.
При необходимости production сначала читать workspace `AGENTS.md`, `LAPTOP-AGENT.md`, `HANDOFF-SafarVPN-2026-10-14-ACCOUNT-PROFILES.md`, `auth-integration/account-profiles-20261014/DEPLOYED.md`.
Последний известный backend image из предыдущего handoff: `sha256:d181ede1c2c7b62f61c507acb04a0a13465bc648ef460ec33d0025d5b1607031`; здесь заново состояние production не проверялось.
Recovery authority: remote `/root/safar-account-profiles-20261014`, controller `package/panel-forward.py`.
Не выполнять old DB/state restore, schema downgrade, legacy node/JSON writer rollback или whole-tree build грязного host bot checkout. №28 не отзывать по старым handoff.
Windows SSH: `& .\ssh_node_windows.ps1 <IP> --command 'команда'`; host key проверку не отключать, при несовпадении остановиться. Секреты/customer records не выводить.

## Старт следующего чата

Прочитай этот handoff и новое задание пользователя. Код 1.4.9/code47 сохранён в пользовательском GitHub, APK локально. Сохраняй работающие аккаунт, подписки, ручные профили и VPN. Прод не менять без причины. Для следующей сборки увеличить versionCode, оставить исходную подпись и подготовить отдельный APK. Без субагентов и ADB без нового разрешения.
