# Minecraft Plot Plugin

Plugin dodający działki na serwer Minecraft.

## Funkcje

- Tworzenie działek za pomocą specjalnego przedmiotu "Serce Działki"
- Ochrona działek przed graczami spoza działki
- System zaproszeń i członkostwa w działkach
- Teleportacja do działek
- Ustawianie własnego punktu teleportacji
- Komunikaty o wejściu/wyjściu z działki

## Komendy

- `/dzialka daj` - Daje Serce Działki (wymaga permisji `dzialka.admin.daj`)
- `/dzialka usun <TAG>` - Usuwa działkę (wymaga permisji `dzialka.admin.usun`)
- `/dzialka dom` - Teleportuje do domu działki
- `/dzialka tp <TAG>` - Teleportuje do działki (wymaga permisji `dzialka.admin.tp`)
- `/dzialka ustaw` - Ustawia punkt teleportacji działki
- `/dzialka zapros <gracz>` - Zaprasza gracza do działki
- `/dzialka dolacz <TAG>` - Dołącza do działki po otrzymaniu zaproszenia

## Permisje

- `dzialka.admin.daj` - Pozwala na dawanie Serc Działki
- `dzialka.admin.usun` - Pozwala na usuwanie działek
- `dzialka.admin.tp` - Pozwala na teleportację do działek
- `dzialka.use` - Pozwala na używanie podstawowych komend działek

## Instalacja

1. Pobierz plik JAR z najnowszej wersji
2. Umieść plik w folderze `plugins` na serwerze
3. Uruchom lub zrestartuj serwer

## Kompilacja

Aby skompilować plugin, użyj Maven:

```bash
mvn clean package
```

Skompilowany plik JAR znajdziesz w folderze `target`.