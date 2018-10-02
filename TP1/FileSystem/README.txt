Ce fichier explique comment lancer nos serveurs et utiliser le client

1. compiler avec la command "ant" à partir du dossier "FileSystem"
2. dans un terminal, lancer "rmiregistry&" dans le dossier "bin"
3. remonter d'un niveau et lancer "authServer.sh"
4. dans un autre terminal, lancer "fileServer.sh"
5. dans un autre terminal, lancer "client.sh" en envoyant la commande désirée

Voici la liste des commandes et leur syntaxe :

-i ip_adress
list
create nomDeFichier
get nomDeFichier
push nomDeFichier
lock nomDeFichier
syncLocalDirectory

6. relancer "client.sh" pour chaque nouvelle commande à exécuter

Le paramètre -i est optionnel, localhost est utilisé par défaut. Nous n'avons pas pu tester sur 
Openstack car le site était inacessible pendant les 3 jours précédent la remise. 
Tous nos tests avaient été en local même avant ces 3 jours.

Vous pouvez utiliser le script clean.sh pour réinitialiser tous les fichiers et comptes des serveurs 
(en assumant que vous lancerez tout en local).