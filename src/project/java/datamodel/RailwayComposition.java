package project.java.datamodel;

import javafx.application.Platform;
import project.java.Controller;
import project.java.Main;
import project.java.datamodel.enums.LocomotiveType;
import project.java.datamodel.enums.VehicleDirection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;

import static project.java.datamodel.RailwayStations.*;
import static project.java.datamodel.Railroads.*;


public class RailwayComposition implements Runnable{
    private LinkedList<RailwayVehicle> composition = new LinkedList<>();
    private int speed;
    private Controller controller;
    private LocomotiveType initialLocomotive;
    private Thread thread;
    private Position start, end;
    private static int fileID = 1;

    public RailwayComposition(Controller controller, int speed){
        this.controller = controller;
        if(speed < 500)
            this.speed = 500;
        else if(speed > 1000)
            this.speed = 1000;
        else
            this.speed = speed;
        thread = new Thread(this);
        thread.setDaemon(true);
    }

    public void addRailwayVehicle(RailwayVehicle vehicle){
        if(vehicle != null){
            if(composition.isEmpty() && vehicle instanceof Locomotive){
                composition.add(vehicle);
                initialLocomotive = ((Locomotive) vehicle).getLocomotiveType();
            }
            else if(vehicle instanceof Wagon ||
                    (vehicle instanceof Locomotive && isCompatible((Locomotive) vehicle))){
                composition.add(vehicle);
            }
        }
    }

    private boolean isCompatible(Locomotive locomotive){
        LocomotiveType locomotiveType = locomotive.getLocomotiveType();
        switch(this.initialLocomotive){
            case Passenger:{
                if(locomotiveType == LocomotiveType.Passenger || locomotiveType == LocomotiveType.Universal)
                    return true;
            }break;
            case Cargo:{
                if(locomotiveType == LocomotiveType.Cargo || locomotiveType == LocomotiveType.Universal)
                    return true;
            }break;
            case Universal:{
                return true;
            }
            case Maintenance:{
                if(locomotiveType == LocomotiveType.Maintenance || locomotiveType == LocomotiveType.Universal)
                    return true;
            }break;
        }
        return false;
    }

    public void addComposition(Position position, Set<Position> railroads) {
        Position newPosition = position;
        VehicleDirection newDirection = composition.get(0).getDirection();

        for(RailwayVehicle v : composition){
            Position temp = new Position(v.getI(), v.getJ());
            VehicleDirection tempDirection = v.getDirection();

            v.setI(newPosition.getI());
            v.setJ(newPosition.getJ());
            v.setDirection(newDirection);

            if(v.getI() != -1)
                controller.addVehicle(newPosition, v);

            v.setVisible(railroads.contains(newPosition));

            newPosition = temp;
            newDirection = tempDirection;
        }
    }

    private void compositionStop(Set<Position> railroads){
       RailwayVehicle locomotive = composition.getFirst();
       Position finalPosition = new Position(locomotive.getI(), locomotive.getJ());
        VehicleDirection newDirection = composition.get(0).getDirection();
       Position newPosition = finalPosition;
       for(RailwayVehicle v : composition){
           Position temp = new Position(v.getI(), v.getJ());
           VehicleDirection tempDirection = v.getDirection();
           if(!v.getPosition().equals(finalPosition)){
               v.setI(newPosition.getI());
               v.setJ(newPosition.getJ());
               v.setDirection(newDirection);
               controller.addVehicle(newPosition, v);
               v.setVisible(railroads.contains(newPosition));

               newPosition = temp;
               newDirection = tempDirection;
           }
       }
    }

    private void compositionRotation(){
        for(RailwayVehicle v : composition) {
            switch (v.getDirection()) {
                case Up:
                    v.rotate(90);
                    break;
                case Down:
                    v.rotate(270);
                    break;
                case Left:
                    v.rotate(0);
                    break;
                case Right:
                    v.rotate(180);
                    break;
            }
        }
    }

    @Override
    public void run() {
        Set<Position> system = new HashSet<>(Railroads.railroadSystem);
        LinkedList<Position> temp = Railroads.BFS(start, end, system);
        int sleepTime = speed;
        int tripDuration = 0;
        Position lastStation = null;
        Position previousStation = null;
        LinkedHashSet<Position> stationsTraveled = new LinkedHashSet<>();
        LinkedList<Position> stationsList = null;
        boolean derail;
        boolean noAlternatives = false;

        RailwayVehicle locomotive = composition.getFirst();
        RailwayVehicle lastVehicle = composition.getLast();

        //!Railroads.stations.contains(temp.get(i)) && controller.hasVehicle(temp.get(i))
        //controller.isOccupied(temp.get(i)) && !end.equals(temp.get(i))
        while(!locomotive.getPosition().equals(end)){
            derail = false;
            if(departureBtoC)
                system = closeRailroad(positionsBtoC);
            if(departureEtoC)
                system = closeRailroad(positionsCtoE);
            temp = Railroads.BFS(start, end, system);
            if(!temp.contains(end)){
                noAlternatives = true;
                system = new HashSet<>(Railroads.railroadSystem);
            }
            for(int i = 0; i < temp.size(); i++){
                if(Railroads.stations.contains(temp.get(i)) && setLastStation(temp.get(i)) != null){
                    lastStation = setLastStation(temp.get(i));
                    stationsTraveled.add(lastStation);
                    stationsList = new LinkedList<>(stationsTraveled);
                    if(stationsList.size() > 1)
                        previousStation = stationsList.get(stationsList.size() - 2);
                }
                if(previousStation != null)
                    clearRailroad(previousStation, lastStation);

                try {
                    synchronized (this) {
                        while ((!Railroads.stations.contains(temp.get(i)) && controller.hasVehicle(temp.get(i)))
                                || isRailroadOccupied(temp.get(i), lastStation)){
                            if(isRailroadOccupied(temp.get(i), lastStation)){
                                Platform.runLater(() -> compositionStop(Railroads.railroads));
                                wait(sleepTime);
                                if(lastStation.equals(stationC) && !temp.getLast().equals(stationD) && !noAlternatives){
                                    derail = true;
                                    start = lastStation;
                                    break;
                                }
                            }
                            wait(10);
                            tripDuration += 10;
                        }
                    }
                } catch (InterruptedException e) {
                    Main.logger.log(Level.WARNING, "Thread interrupted.", e);
                }

                if(derail)
                    break;

                int j = i + 1;
                if(j < temp.size()){
                    Position pos1 = temp.get(i);
                    Position pos2 = temp.get(j);
                    composition.get(0).setDirection(RoadVehicle.comparePositions(pos1, pos2));
                }
                compositionRotation();

                if(stations.contains(temp.get(i)))
                    openRamp(lastStation);

                int finalI = i;
                LinkedList<Position> finalTemp = temp;
                Platform.runLater(() -> addComposition(finalTemp.get(finalI), Railroads.railroads));
                setDeparture(temp.get(i), lastStation);
                tripDuration += sleepTime;
                RailwayStations.closeRamp(temp.get(i));
                try{
                    Thread.sleep(sleepTime);
                }catch (InterruptedException e){
                    Main.logger.log(Level.WARNING, "Thread interrupted.", e);
                }
            }
        }
        while(!lastVehicle.getPosition().equals(end)){
            Platform.runLater(() -> compositionStop(Railroads.railroads));
            compositionRotation();
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Main.logger.log(Level.WARNING, "Thread interrupted.", e);
            }
            tripDuration += sleepTime;
        }

        String filePath = controller.historyFolder + File.separator + "movHistory" + (fileID++) + ".ser";
        try(ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(filePath))){
            MovementHistory history = new MovementHistory(tripDuration, temp, stationsList, composition);
            os.writeObject(history);
        }catch (IOException e){
            Main.logger.log(Level.WARNING, "File error, can't serialize object.", e);
        }

        for(RailwayVehicle v : composition)
            Platform.runLater(() -> controller.removeVehicle(end, v));
    }

    public void go(){ thread.start(); }

    public void setStart(Position start){ this.start = start; }

    public void setEnd(Position end){ this.end = end; }

    private synchronized boolean isRailroadOccupied(Position position, Position lastStation){
        if(lastStation.equals(Railroads.stationA) && positionsAtoB.contains(position) && departureBtoA)
            return true;
        else if(lastStation.equals(Railroads.stationA) && positionsAtoE.contains(position) && departureEtoA)
            return true;
        else if(lastStation.equals(Railroads.stationB) && positionsAtoB.contains(position) && departureAtoB)
            return true;
        else if(lastStation.equals(Railroads.stationB) && positionsBtoC.contains(position) && departureCtoB)
            return true;
        else if(lastStation.equals(Railroads.stationC) && positionsBtoC.contains(position) && departureBtoC)
            return true;
        else if(lastStation.equals(Railroads.stationC) && positionsCtoD.contains(position) && departureDtoC)
            return true;
        else if(lastStation.equals(Railroads.stationC) && positionsCtoE.contains(position) && departureEtoC)
            return true;
        else if(lastStation.equals(Railroads.stationD) && positionsCtoD.contains(position) && departureCtoD)
            return true;
        else if(lastStation.equals(Railroads.stationE) && positionsCtoE.contains(position) && departureCtoE)
            return true;
        else return lastStation.equals(Railroads.stationE) && positionsAtoE.contains(position) && departureAtoE;
    }

    private synchronized Position setLastStation(Position position){
        Position leftPosition, downPosition, ePosition;
        leftPosition = new Position(position.getI() - 1, position.getJ());
        downPosition = new Position(position.getI(), position.getJ() + 1);
        ePosition = new Position(position.getI() - 1, position.getJ() + 1);
        if(position.equals(stationA) || leftPosition.equals(stationA) || downPosition.equals(stationA) ||ePosition.equals(stationA))
            return stationA;
        else if(position.equals(stationB) || leftPosition.equals(stationB) || downPosition.equals(stationB) ||ePosition.equals(stationB))
            return stationB;
        else if(position.equals(stationC) || leftPosition.equals(stationC) || downPosition.equals(stationC) ||ePosition.equals(stationC))
            return stationC;
        else if(position.equals(stationD) || leftPosition.equals(stationD) || downPosition.equals(stationD) ||ePosition.equals(stationD))
            return stationD;
        else if(position.equals(stationE) || leftPosition.equals(stationE) || downPosition.equals(stationE) ||ePosition.equals(stationE))
            return stationE;
        else
            return null;
    }

    private synchronized void setDeparture(Position position, Position lastStation){
        if(positionsAtoB.contains(position) && lastStation.equals(stationA))
            departureAtoB = true;
        else if(positionsAtoB.contains(position) && lastStation.equals(stationB))
            departureBtoA = true;
        else if(positionsBtoC.contains(position) && lastStation.equals(stationB))
            departureBtoC = true;
        else if(positionsBtoC.contains(position) && lastStation.equals(stationC))
            departureCtoB = true;
        else if(positionsAtoE.contains(position) && lastStation.equals(stationA))
            departureAtoE = true;
        else if(positionsAtoE.contains(position) && lastStation.equals(stationE))
            departureEtoA = true;
        else if(positionsCtoE.contains(position) && lastStation.equals(stationC))
            departureCtoE = true;
        else if(positionsCtoE.contains(position) && lastStation.equals(stationE))
            departureEtoC = true;
        else if(positionsCtoD.contains(position) && lastStation.equals(stationC))
            departureCtoD = true;
        else if(positionsCtoD.contains(position) && lastStation.equals(stationD))
            departureDtoC = true;
    }

    private synchronized void clearRailroad(Position previousStation, Position lastStation){
        if(previousStation.equals(stationA) && lastStation.equals(stationB))
            departureAtoB = false;
        else if(previousStation.equals(stationA) && lastStation.equals(stationE))
            departureAtoE = false;
        else if(previousStation.equals(stationB) && lastStation.equals(stationA))
            departureBtoA = false;
        else if(previousStation.equals(stationB) && lastStation.equals(stationC))
            departureBtoC = false;
        else if(previousStation.equals(stationC) && lastStation.equals(stationB))
            departureCtoB = false;
        else if(previousStation.equals(stationC) && lastStation.equals(stationD))
            departureCtoD = false;
        else if(previousStation.equals(stationC) && lastStation.equals(stationE))
            departureCtoE = false;
        else if(previousStation.equals(stationD) && lastStation.equals(stationC))
            departureDtoC = false;
        else if(previousStation.equals(stationE) && lastStation.equals(stationC))
            departureAtoE = false;
        else if(previousStation.equals(stationE) && lastStation.equals(stationA))
            departureEtoA = false;
    }

    private synchronized void openRamp(Position lastStation){
        if(lastStation.equals(stationA))
            closeLeftRoad = false;
        else if(lastStation.equals(stationB)){
            closeLeftRoad = false;
            closeMiddleRoad = false;
        }
        else if(lastStation.equals(stationC)){
            closeMiddleRoad = false;
            closeRightRoad = false;
        }
        else if(lastStation.equals(stationE))
            closeRightRoad = false;
    }

    public int getCompositionSize(){ return composition.size(); }
}

