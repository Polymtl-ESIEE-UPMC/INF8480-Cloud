Ce document indique comment effectuer les tests de performance demandé.
Note: Tous les serveurs peuvent être démarrés sur un poste du L4712
différent en ssh.
_________________________
Mode sécurisé:
1. Démarrer le service de nom en lançant le script authServer.sh
2. Démarrer le nombre serveur de calcul voulu, chacun dans une console différente
   en lançant le script calculationServer.sh en spécifiant l'adresse ip du service de nom,
   le port voulu pour le serveur de calcul (entre 5002 et 5050) et la capacité voulue
   Exemple : ./calculationServer.sh -i 132.207.12.44 -p 5002 -c 4
   Un message dans la console du service de nom devrait apparaitre indiquant le succès 
   d'enregistrement du serveur.
3. Démarrer le répartiteur en lançant le script repartiteur.sh en spécifiant l'adresse
   ip du service de nom et le mode sécurisé avec l'option -s
   Exemple : ./repartiteur.sh -i 132.207.12.44 -s
   Une liste des serveurs reçus devrait être écrite en console.
4. Lancer le script client.sh avec comme paramètres l'adresse ip du service de nom et 
   le fichier d'opérations à effectuer.
   Exemple : ./client.sh -i 132.207.12.44 operations-588

Lors de l'ajout de serveur de calcul, il faut redémarrer le répartiteur pour qu'il reçoive 
les nouveaux serveurs.

_________________________
Mode non-sécurisé:
1. Démarrer le service de nom en lançant le script authServer.sh
2. Démarrer 4 serveurs de calcul, comme avant, mais en ajoutant le paramètre 
   -m pour le serveur malicieux suivi du % voulu
   Exemple : ./calculationServer.sh -i 132.207.12.44 -p 5002 -c 4 -m 50
   Ce serveur sera malicieux 50% du temps
3. Démarrer le répartiteur en lançant le script repartiteur.sh en spécifiant l'adresse
   ip du service de nom sans le paramètre -s
   Exemple : ./repartiteur.sh -i 132.207.12.44
   Le mode sera non-sécurisé
4. Lancer le script client.sh avec comme paramètres l'adresse ip du service de nom et 
   le fichier d'opérations à effectuer.
   Exemple : ./client.sh -i 132.207.12.44 operations-588

Il est mieux fermer le service de nom, le répartiteur et chaque serveur de calcul entre
chaque test pour ne pas avoir de duplicat dans la liste du service de nom.
