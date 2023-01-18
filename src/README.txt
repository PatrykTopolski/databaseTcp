Projekt programistyczny z przedmiotu SKJ – semestr zimowy 2022/23
Temat: Rozproszona baza danych
Autor: Patryk Topolski S26455

4.4.1:
Przegląd
Ten dokument opisuje organizację sieciowego systemu baz danych, w tym protokół komunikacyjny używany między węzłami i klientami.
System składa się z wielu węzłów bazy danych, które można ze sobą łączyć, tworząc rozproszoną sieć.
Każdy węzeł jest odpowiedzialny za przechowywanie i zarządzanie JEDNYM Rekordem bazy. Klienci mogą łączyć się z dowolnym węzłem w sieci i wysyłać polecenia w celu wykonania operacji na bazie danych.

Organizacja sieci
Sieć jest zorganizowana jako graf, a każdy węzeł łączy się z dowolną ilością węzłów.
Kiedy nowy węzeł dołącza do sieci, łączy się z istniejącym węzłem i daje mu o sobie znać, by ten mógł dodać go do swojej listy węzłów.
Węzły przechowywują informacje o połączonych z nimi węzłach i w razie potrzeby tworzą gniazdo na podstawie tych informacji aby się z nimi połączyć
Pozwala to na łatwe dodawanie i usuwanie węzłów z sieci.

Protokół komunikacyjny
Protokół komunikacyjny zastosowany w systemie oparty jest na protokole TCP. Każdy węzeł uruchamia wątek z gniazdem serwera,
które nasłuchuje połączeń przychodzących od klientów i innych węzłów. Kiedy klient lub inny węzeł łączy się z węzłem, Węzeł tworzy nowy wątek do obsługi komunikacji i dodaje go do ThreadPool executora co ma na celu 2 rzeczy-
obsługę wielu zapytań jednocześnie, oraz pomoc przy bardziej skomplikowanych zapytaniach jak get-max czy get-min

Wiadomości
Do komunikacji system wykorzystuje proste komunikaty tekstowe. Każda wiadomość to pojedyncza linia tekstu zakończona znakiem nowej linii.
Pierwszym słowem komunikatu jest polecenie, po którym mogą występować dodatkowe argumenty. Poniżej opisano możliwe polecenia i ich argumenty.

get-value key: To polecenie jest wysyłane przez klienta do węzła w celu pobrania wartości powiązanej z danym kluczem. Węzeł odpowiada wartością lub ERROR, jeśli klucza nie ma w bazie danych.
Zaraz po otrzymaniu polecenia od Clienta, Node sprawdzi sam siebie, jeżeli szukany klucz nie należy do niego, zacznie odpytywać połączone z nim nody dodając do komendy i klucza UUID (universally unique identifier), węzły które otrzymają takie polecenie
sprawdzą czy nie przetworzyły już takiego zapytania, jeżeli nie, zrobią to samo co poprzedni tym razem jednak przesyłając dalej UUID z zapytania, jeżeli przetworzyły już ten komunikat po prostu zakończą połączenie
Komunikaty wysyłane do następnych Węzłów będą wyglądać następująco: get-value [key] [UUID]
java DatabaseClient -gateway [address]:[port] -operation get-value [key]

set-value klucz:wartość: To polecenie jest wysyłane przez klienta do węzła w celu zaktualizowania wartości powiązanej z danym kluczem. Węzeł odpowiada OK, jeśli aktualizacja się powiodła, lub ERROR, jeśli klucza nie ma w bazie danych.
Zasada działania Z wykorzystaniem UUID tak samo jak powyżej.
Komunikaty wysyłane do następnych Węzłów będą wyglądać następująco: set-value [key]:[value] [UUID]
java DatabaseClient -gateway [address]:[port] -operation set-value [key]:[value]

find-key key: To polecenie jest wysyłane przez klienta do węzła w celu zlokalizowania węzła odpowiedzialnego za dany klucz. Węzeł odpowiada adresem węzła odpowiedzialnego za klucz.
Zasada działania Z wykorzystaniem UUID tak samo jak powyżej.
Komunikaty wysyłane do następnych Węzłów będą wyglądać następująco: find-key [key] [UUID]
java DatabaseClient -gateway [address]:[port] -operation find-value [key]

new-record [key]:[value]: To polecenie zmieni klucz i wartość w węźle który odpytujemy.
java DatabaseClient -gateway [address]:[port] -operation new-record [key]:[value]
Tutaj nie było potrzeby implementacji UUID

get-max : to polecenie postara się znaleźć najwyższą wartość w grafie oraz zwróci parę [key]:[value] Węzła z najwyższą wartością - zawsze zwróci jakąś wartość. To zapytanie oprócz opisanego powyżej mechanizmu UUID, będzie śledzić odwiedzone już Nody z użyciem
Kolekcji SET, i nie będzie odpytywać tam umieszczonych węzłów, oprócz tego, od poprzedniego Noda otrzyma informacje o nim dzięki czemu nie będzie już go pytać. Tutaj też najbardziej skorzystałem z Mechaniki wielu wątków, na przykład:
Posiadamy graf w formie pierścienia, pierwszy Węzeł pyta węzeł obok ten tak samo i tak aż do ostatniego który odpyta pierwszy, wtedy ten stworzy nowy Wątek (pierwszy wątek czeka na odpowiedź od kolejnego) i zobaczy, że on już pytał więc zerwie połączenie
pozwalając cofnąć się poprzednim wątkom dzięki czemu unikamy problemów w stylu "Dead lock" czy "infinite loop"
Komunikaty wysyłane do następnych Węzłów będą wyglądać następująco: get-max [UUID] [Port]
java DatabaseClient -gateway [address]:[port] -operation get-max

get-min : tak samo jak w get max ale przeszuka Graf w poszukiwaniu najniższej wartości.
Komunikaty wysyłane do następnych Węzłów będą wyglądać następująco: get-min [UUID] [Port]
java DatabaseClient -gateway [address]:[port] -operation get-min

terminate: to polecenie jest wysyłane przez klienta do węzła w celu zakończenia węzła. Węzeł ten powiadomi najpierw węzły z nim połączone aby mogły usunąć go z listy swoich połączeń zanim sam się wyłączy pozwalając najpierw wątkom dokończyć pracę.
java DatabaseClient -gateway [address]:[port] -operation terminate.
Komunikaty wysyłane do następnych Węzłów będą wyglądać następująco: terminating [host]:[port]

Obsługa błędów
W przypadku błędu komunikacji lub nieprawidłowego polecenia węzeł odpowie komunikatem ERROR: Invalid command + komenda.

Uwagi Autora:
W projekcie wykorzystałem HashMap w celu przechowywania Danych, w trakcie pisania zorientowałem się, że powinniśmy założyć że Każdy węzeł utrzymuje jedną parę, pozostawiłem tą Kolekcje żeby nie zmieniać struktury kodu i być może później rozbudować projekt.
ThreadPool jest typem Cashed - daje to zaletę dostosowywania używanych wątków do ilosci zapytań dodawania ich jeżeli jest taka potrzeba i wykorzysywania ich ponownie kiedy się da, jeżeli pula wątków przekroczy domyślną ich dopuszczalną liczbę, ThreadPool
zacznie kolejkować Wątki w celu poprawienia Wydajności.

przykładowy ciąg działania programu: Uruchamiamy CLient z celem rządaniem i danymi, client przekazuje te rządanie do wskazanego Węzła, węzeł na specjalnie wyznaczonym do tego zadania wątku przyjmie zapytanie i wrzuci nowy wątek do ThreadPoola,
Wątek uruchamia metodę która zajmuje się obsługą zapytania.



4.4.2 jak skompilować i zainstalować (z linii poleceń bez używania GUI):
za pomocą compile.bat

4.4.3 co zostało zaimplementowane:
Wszystko wyszczególnione w opisie projektu

4.4.4 co nie działa (jeśli nie działa):
-

