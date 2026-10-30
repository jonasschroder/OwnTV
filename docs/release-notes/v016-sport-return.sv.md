# Min TV v0.16 – Sport och återgång från uppspelning

- Återgång från helskärm i Sport begär fokus först när sidan har skapats igen och den lokala matchcachen har lästs. Befintlig kort- och rullningsposition återanvänds när den är tillgänglig.
- Saknat innehåll ger en säker återgång till Sport i toppraden. Egen navigation avbryter väntande fokusåterställning, och en annan profil ärver inte föregående profils mål.
- Kontroll på Chromecast med befintlig v0.15 visade fungerande kanalstart via Färjestads nästa match och bekräftad bild/ljud. Det är separat från verifiering av vilken kanal som faktiskt sänder matchen.

Returfixen har verifierats med riktade tester i 1080p och 720p på emulator. Ny kod har inte installerats på Chromecast. Ingen ändring av spelarmotor, kanalval, IPTV-data, signering eller publicerad release.
