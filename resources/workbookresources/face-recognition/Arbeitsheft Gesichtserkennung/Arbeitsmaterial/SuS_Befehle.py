import os
import matplotlib.pyplot as plt
import numpy as np

names=["Ariel_Sharon","Colin_Powell","Donald_Rumsfeld","George_W_Bush","Gerhard_Schroeder","Hugo_Chavez","Junichiro_Koizumi","Tony_Blair"]
AnzahlBilder=[77,236,121,530,109,71,60,144]


#extrahiert Zahl aus einem Datei Pfad
#um nach den Nummern in den Pfaden zu sortieren
def extrahiere_zahl(name):
    zahl = ''.join([c for c in name if c.isdigit()])
    return int(zahl) if zahl else -1


#gibt Bild als NP Array aus
def getImage(Person_Nr,Bild_Nr):
	#aktuelles Verzeichnis
	aktuelle_pos=os.getcwd()
	path=aktuelle_pos+"/importierte_bilder/"+names[Person_Nr]+"/"
	data = [f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))]
	data=sorted(data, key=extrahiere_zahl)
	path+=data[Bild_Nr]
	bild_array = plt.imread(path)
	return bild_array
	
#gibt Gatter in Form eines Bildes als NP Array aus
def getGatter(Person_Nr,Bild_Nr):
	#aktuelles Verzeichnis
	aktuelle_pos=os.getcwd()
	path=aktuelle_pos+"/gatter_bilder/"+names[Person_Nr]+"/"
	data = [f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))]
	data=sorted(data, key=extrahiere_zahl)
	path+=data[Bild_Nr]
	bild_array = plt.imread(path)
	return bild_array
	
#gibt Gatter als Punkte Vektor (NP Array) aus	
def getNpArray(Person_Nr,Bild_Nr):
	#aktuelles Verzeichnis
	aktuelle_pos=os.getcwd()
	path=aktuelle_pos+"/np_arrays/"+names[Person_Nr]+"/"
	data = [f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))]
	data=sorted(data, key=extrahiere_zahl)
	path+=data[Bild_Nr]
	bild_array = np.load(path)
	return bild_array

#zeigt Bild und Gatter-Bild nebeneinander
def Zeig_Bild_und_Gatter(Person_Nr,Bild_Nr):
	bild=getImage(Person_Nr,Bild_Nr)
	gatter_bild=getGatter(Person_Nr,Bild_Nr)
	fig, axs = plt.subplots(1, 2, figsize=(10, 5))

	
	# Erstes Bild anzeigen
	axs[0].imshow(bild)
	axs[0].axis('off')
	axs[0].set_title(str(Bild_Nr)+"tes Bild von "+names[Person_Nr])

	# Zweites Bild anzeigen
	axs[1].imshow(gatter_bild, cmap="gray")
	axs[1].axis('off')
	axs[1].set_title(str(Bild_Nr)+"tes Gatter von "+names[Person_Nr])

	# Anzeigen
	plt.tight_layout()
	plt.show()
	
#beschreibt die Funktion der verschiedenen Methoden	
def help():
	print("Es stehen Bilder von ",len(names)," Personen zur Verfügung")
	for i in range(len(names)):
		print("Person Nr.",i," heißt ",names[i]," und hat ",AnzahlBilder[i]," Bilder.")
	print("Die folgenden Befehle stehen zur Verfügung:")
	print("a steht für die Person Nummer.")
	print("b steht für die Bild Nummer.")
	print("getImage(a,b)")
	print("gibt das Bild als NP_Array aus.")
	print("getGatter(a,b)")
	print("gibt das Gatter-Bild als NP_Array aus.")
	print("getNpArray(a,b)")
	print("gibt die Koordinaten der markanten Punkte als NP_Array aus.")
	print("Zeig_Bild_und_Gatter(a,b)")
	print("Zeigt das Bild und Gatter nebeneinander.")
	print("test(k)")
	print("der k-nearest-neighbours Algorithmus wird für einige Bilder ausprobiert")
	print("k ist frei wählbar (max. 350)")
	
	
	
	
	
	
	
	
	
	
	
	
def generate_cluster():
	daten=[]
	for i in range(len(names)):
		for j in range(40):
			A=getNpArray(i,j)
			if len(A)>10:
				daten.append([A,names[i]])
	return daten
	
def abstand(P1,P2):
	d=0
	for i in range(len(P1)):
		d+=(P1[i][0]-P2[i][0])**2
		d+=(P1[i][1]-P2[i][1])**2
	d=d**(1/2)
	return d 

def sort_by_abstand(daten,neu):
	sort=sorted(daten, key=lambda punkt: abstand(punkt[0], neu))
	return sort
	
def knn(daten,neu,k):
	eigenschaften=[] #Liste der Zuordnung der k-nächsten-Nachbarn
	s_daten=sort_by_abstand(daten,neu)
	nachbarn=s_daten[:k]
	for i in nachbarn:
		eigenschaften.append(i[1])
	eigenschaft=max(set(eigenschaften), key=eigenschaften.count)
	return eigenschaft	#Ausgabe der am häufigsten Zuordnung
	
	
def test(k):
	for i in range(len(names)):
		print("-----------------------")
		print(i,"-ter Versuch")
		print("Es wurde ",knn(generate_cluster(),getNpArray(i,54),k)," zugeordnet.")
		print("Es sollte ",names[i]," seien.")
	
	
	

	
