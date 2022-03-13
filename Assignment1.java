import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class Logger {
    PrintStream file;
    PrintStream cmd;

    Logger() {
        File f = new File ("./output.dat");
        try {f.createNewFile();}
        catch (IOException e) {}
        try {this.file = new PrintStream(f);}
        catch (FileNotFoundException e) {}
        this.cmd = System.out;
    }

    public void log(String message) {
        //String s = String.format("[%s] %s", LocalTime.now().truncatedTo(ChronoUnit.SECONDS), message);
        String s = message;
        System.setOut(this.file);
        System.out.println(s);
        System.setOut(this.cmd);
        System.out.println(s);
    }
}
public class Assignment1 {
    static long randomSeed = 0;
    Logger logger = new Logger();

    AtomicBoolean continueOrders = new AtomicBoolean(true); // Controls ordering loop
    Integer millisecondsPerTic = 5;
    Integer programDuration = 250;

    // How frequently dealership makes an order
    Integer unpainted_arrival_tics;

    // Maximum capacity per car model
    Integer warehouse_capacity;

    // Defining car models and colours
    List<String> car_models;
    List<String> colours;

    // Counter to ensure unique identifier for new vehicles
    AtomicInteger uidCount;

    // Time to sleep between dealership placing orders
    Integer dealershipSleep;


    Assignment1()
    {
        this.unpainted_arrival_tics = 20;
        this.warehouse_capacity = 30;
        this.car_models =
                new ArrayList<String>(
                        Arrays.asList("3", "S", "Y", "X", "Roadster")
                );
        this.colours =
                new ArrayList<String>(
                        Arrays.asList("grey", "black", "white", "beige", "blue", "red", "green")
                );
        this.uidCount = new AtomicInteger(0);
        this.dealershipSleep = millisecondsPerTic;

    }
    public static void main(String args[]) {
        // Initialisation, main code here
        Assignment1 assignment1 = new Assignment1();
        Queue<Vehicle> carrier_trailer = new LinkedList<Vehicle>();

        // Create Robots for production line
        List<Robot> robots = new ArrayList<Robot>();

        // 5
        Robot painter = new Robot("Painter", "Trailer", 20, assignment1, carrier_trailer);
        robots.add(painter);
        // 4
        Robot electrodipper = new Robot("Electrodipper", "Painting", 40, assignment1, painter.waiting_vehicles);
        robots.add(electrodipper);
        // 3
        Robot primer = new Robot("Primer", "Electrodipping", 30, assignment1, electrodipper.waiting_vehicles);
        robots.add(primer);
        // 2
        Robot washer = new Robot("Washer", "Priming", 10, assignment1, primer.waiting_vehicles);
        robots.add(washer);
        // 1
        Robot sander = new Robot("Sander", "Washing", 5, assignment1, washer.waiting_vehicles);
        robots.add(sander);

        // Starting up robots for the production line
        ExecutorService workerExecutor = Executors.newFixedThreadPool(6);
        robots.forEach(robot -> workerExecutor.execute(robot));

        // Creating a warehouse object, this handles various functionality related to accepting orders (...)
        // (...) from the dealership, as well as passing cars to/from robots on the production line.
        Warehouse warehouse = new Warehouse(
                sander.waiting_vehicles,
                carrier_trailer,
                assignment1);

        // Timer task to stop ordering of vehicles
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                assignment1.continueOrders.set(false);
                // End timer thread once task is executed
                timer.cancel();
            }
        };
        timer.schedule(task, assignment1.millisecondsPerTic * assignment1.programDuration);

        // Dealership ordering vehicles starts here
        Dealership dealership = new Dealership(assignment1, warehouse);
        workerExecutor.execute(dealership);

    }

}

class Vehicle {
    Integer uid;
    Integer price;
    String model;
    String colour;
    String status;
    Vehicle(String model, String colour, Integer uid) {
        this.uid = uid;
        this.price = 20000;
        this.model = model;
        this.colour = colour;
        this.status = "Unpainted";
    }
}

class Warehouse {
    // tics between new cars arriving
    Integer arrival_tics;
    Dictionary<String, AtomicInteger> current_stock;
    Integer limit_per_model;
    private Queue<Vehicle> placed_orders;
    Queue<Vehicle> carrier_trailer;
    AtomicInteger trailer_counter;
    List<String> car_models;
    Assignment1 assignment1;

    Warehouse(Queue<Vehicle> first_robot_queue,
              Queue<Vehicle> trailer,
              Assignment1 assignment1) {
        this.arrival_tics = assignment1.unpainted_arrival_tics;
        this.current_stock = new Hashtable<String, AtomicInteger>();
        assignment1.car_models.forEach(car -> current_stock.put(car, new AtomicInteger(5)));

        this.limit_per_model = assignment1.warehouse_capacity;
        this.placed_orders = first_robot_queue;
        this.carrier_trailer = trailer;
        this.trailer_counter = new AtomicInteger(0);
        this.car_models = assignment1.car_models;
        this.assignment1 = assignment1;
    }

    public synchronized void placeOrder(List<String> car_models, List<String> colours, AtomicInteger uidCount){
        // Model/Colour are randomised here, but if you wanted to, could have randomisation/selection done in (...)
        // (...) the dealership too. It's functionally equivalent, leads to the same result, it's just easier to (...)
        // (...) work with it this way because of the way I initially planned out these classes.
        Random rng = new Random(Assignment1.randomSeed);
        String random_model = car_models.get(rng.nextInt(car_models.size()));
        String random_colour = colours.get(rng.nextInt(colours.size()));
        Integer uid = uidCount.incrementAndGet();

        Vehicle vehicle = new Vehicle(random_model, random_colour, uid);
        // Checking stock in warehouse.
        if (current_stock.get(vehicle.model).get() > 0) {
            current_stock.get(vehicle.model).decrementAndGet();

            // Redirecting output so logs go out to console as well as the file.
            String outp = ("Received order for vehicle model " + vehicle.model + ", colour " + vehicle.colour + " => There are " + current_stock.get(vehicle.model).get() + " of this model left in stock.");
            assignment1.logger.log(outp);

            synchronized (placed_orders)
            { placed_orders.add(vehicle);
            placed_orders.notify(); }
        }
    }
    public synchronized void checkCarrierTrailer () {
        if (carrier_trailer.size() >= 3) {
            synchronized (carrier_trailer)
            { carrier_trailer.clear();
            carrier_trailer.notify(); }

            String outp = ("Car carrier trailer departed.");
            assignment1.logger.log(outp);
        }
    }
    public synchronized void  addToWarehouse() {
        Random rng = new Random(Assignment1.randomSeed);
        String random_model = car_models.get(rng.nextInt(car_models.size()));
        AtomicInteger currentStock = current_stock.get(random_model);
        if ( currentStock.get() < limit_per_model)
        {
            currentStock.incrementAndGet();

            String outp = ("A model " + random_model + " vehicle is added to the warehouse => " + currentStock +  " vehicles of model " + random_model + " remaining.");
            assignment1.logger.log(outp);
        }
    }

    // If we have time for adding multiple dealerships, we'll need to move order processing from Robot to Warehouse (...)
    // (...) current solution is safe since only one of the robots currently removes from the queue.
//    public synchronized Object processOrder() {
//        while(placed_orders.size() == 0) {
//            try { this.wait(); }
//            catch (InterruptedException e) {}
//
//        }
//        return placed_orders.remove();
//    }
}

class Robot implements Runnable {
    // Role example: Washing
    String role;
    // Status example: Active
    String status;
    // Which robot the vehicle being worked on is supposed to be sent to
    String sendsTo;
    Integer curr_veh_uid;
    Integer job_length_tics;
    Queue<Vehicle> waiting_vehicles;
    AtomicBoolean continueOrders;
    Assignment1 assignment1;
    Queue<Vehicle> nextStation;

    Robot(String role, String destination, Integer job_length_tics, Assignment1 assignment1, Queue<Vehicle> nextStation) {
        this.role = role;
        this.status = "Ready";
        this.sendsTo = destination;
        this.curr_veh_uid = -1;
        this.job_length_tics = job_length_tics;
        this.waiting_vehicles = new LinkedList<Vehicle>();
        this.continueOrders = assignment1.continueOrders;
        this.assignment1 = assignment1;
        this.nextStation = nextStation;
    }

    @Override
    public void run() {
        Vehicle currentVehicle;
        String outp = ("Robot " + role + " started.");
        assignment1.logger.log(outp);

        while ((continueOrders.get()) | (waiting_vehicles.size() != 0))
        {
            // Sleep to prevent polling continueOrders too often
            try {Thread.sleep(500);}
            catch (InterruptedException e) {}

            // Wait if there's nothing in the queue, will continue once it's notified.
            if (waiting_vehicles.size() == 0) {
                // Wait on waiting vehicles until notified of a new vehicle being added
                try {
                    synchronized (waiting_vehicles) { waiting_vehicles.wait(); } }
                catch (InterruptedException e) {}
            }

            // Spend some time working on the car, using configured tic length from main class, called 'assignment1'
            currentVehicle = waiting_vehicles.remove();

            outp = ("- Thread - " + Thread.currentThread().getId() + ": " + "The " + role + " robot is now working on order " + currentVehicle.uid);
            assignment1.logger.log(outp);

            try { Thread.sleep(assignment1.millisecondsPerTic * job_length_tics); }
            catch (InterruptedException e) {}

            // Once work is completed, move the car to the next queue.
            currentVehicle.status = role;
            outp = ("- Thread - " + Thread.currentThread().getId() + ": " + role + " is done with vehicle " + currentVehicle.uid + ". moving to next stage: " + sendsTo + ".");
            assignment1.logger.log(outp);

            // Car moving
            try {Thread.sleep(100);}
            catch (InterruptedException e) {}
            synchronized (nextStation)
            {
                nextStation.add(currentVehicle);
                nextStation.notify();
            }
        }
    }
}
class Dealership implements Runnable{
    AtomicBoolean continueOrders;
    Warehouse warehouse;
    Assignment1 assignment1;
    public Dealership(Assignment1 assignment1, Warehouse warehouse) {
        this.continueOrders = assignment1.continueOrders;
        this.warehouse = warehouse;
        this.assignment1 = assignment1;
    }
    @Override
    public void run() {
        Random rng = new Random(Assignment1.randomSeed);
        // Flag for continue orders is set to false when specified number of tics pass
        while (continueOrders.get()){
            // 1 in 10 chance that an order is placed
            if (rng.nextInt(11) + 1 == 5) {
                warehouse.placeOrder(assignment1.car_models, assignment1.colours, assignment1.uidCount);

                // Dealership calls the warehouse, asking if the carrier trailer is full yet.
                warehouse.checkCarrierTrailer();
            }
            // Dealership delay between orders
            try { Thread.sleep(assignment1.dealershipSleep); }
            catch (InterruptedException e) {}

            // Dealership calls supplier, asks if more vehicles are ready to be delivered to the warehouse.
            // Random 1 in 7 chance of adding another car to warehouse.
            if (rng.nextInt(8) + 1 == 4) {
                warehouse.addToWarehouse();
            }
        }
    }
}
