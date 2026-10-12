
#sortiert die Daten nach Abstand zu dem neuen Punkt
def sort_by_abstand(daten,neu):
	return sorted(daten, key=lambda punkt: abstand(punkt[0], neu))


#Eingabe: Punkt 1 P1, Punkt 2 P2
#Ausgabe: euklidischer Abstand d
def abstand(P1,P2):	

	#TODO

	return d
	

#Eingabe: Punkte mit Kennzeichnungen: Daten, neuer Punkt: neu, Anzahl der zu betrachtenen Nachbarn: k
#Ausgabe: Kennzeichung, welche der neue Punkt bekommt
def knn(daten,neu,k):
	daten_sorted=sort_by_abstand(daten,neu)
	
	#TODO

	return kennzeichnung	
	
	
	
def test():
	Daten1=[[[0,0],"A"],[[1,1],"A"],[[1,2],"A"],[[10,10],"B"]]
	neu1=[9,9]
	k1=1
	k1_2=3
	
	Daten2=[[[0,0],"A"],[[1,2],"A"],[[2,1],"A"],[[10,10],"B"],[[5,4],"C"],[[4,4],"C"],[[6,4],"C"]]
	neu2=[5,5]
	k2=3
	
	print("Output von knn(): ",knn(Daten1,neu1,k1))
	print("gewünschter Output: B")
	print("Output von knn(): ",knn(Daten1,neu1,k1_2))
	print("gewünschter Output: A")
	print("Output von knn(): ",knn(Daten2,neu2,k2))
	print("gewünschter Output: C")
	
	
	
	
