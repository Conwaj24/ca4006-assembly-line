import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


class OrderCounter {
    static AtomicInteger i = new AtomicInteger(0);
    static AtomicInteger active = new AtomicInteger(0);
    public synchronized static int getID() {
        active.incrementAndGet();
        return i.incrementAndGet();
    }
    public static void release() {
        if (active.decrementAndGet() == 0)
            System.exit(0);
    }
}

class Logger {
    public static void log(String message) {
        String s = String.format("[%s] %s", LocalTime.now().truncatedTo(ChronoUnit.SECONDS), message);
        System.out.println(s);
    }

    public void die(Exception deathRattle) {
        System.err.println(deathRattle);
        System.exit(1);
    }
}

class Station extends LinkedBlockingQueue<Vehicle> implements Runnable {
    String name;
    long threadNumber;

    Station(int capacity, String name) {
        super(capacity);
        this.name = name;
    }

    protected void doWork(Vehicle v) {
    }

    void log(String message) {
        Logger.log(message);
    }

    @Override
    public void run() {
        this.threadNumber = Thread.currentThread().getId();
        log(name + ": Active");
        while (true)
        {
            try {
                doWork(take());
            }
            catch(InterruptedException e) {}
        }
    }
}

class Carrier extends Station {
    Carrier() {
        super(6, "Carrier");
    }
    protected void doWork(Vehicle v) {
        OrderCounter.release();
    }
}

public class Assignment1 {
    static long randomSeed = 0;
    static Logger logger = new Logger();
    static AtomicInteger threadCounter = new AtomicInteger(0);

    AtomicBoolean continueOrders = new AtomicBoolean(true); // Controls ordering loop
    static int millisecondsPerTic = 5;
    static int programDuration = 250;
    Integer orderInterval = 10;
    Integer warehouse_capacity = 30;

    List<String> car_models;
    List<String> colours;

    Assignment1()
    {
        this.car_models =
                new ArrayList<String>(
                        Arrays.asList("3", "S", "Y", "X", "Roadster")
                );
        this.colours =
                new ArrayList<String>(
                        Arrays.asList("grey", "black", "white", "beige", "blue", "red", "green")
                );
    }
    public static void main(String args[]) {
        // Initialisation, main code here
        Assignment1 assignment1 = new Assignment1();

        var robots = new ArrayList<Station>();

        robots.add(new Carrier());
        robots.add(new Robot("Painter", 20, robots.get(robots.size() -1)));
        robots.add(new Robot("Electrodipper", 40, robots.get(robots.size() -1)));
        robots.add(new Robot("Primer", 30, robots.get(robots.size() -1)));
        robots.add(new Robot("Washer", 10, robots.get(robots.size() -1)));
        robots.add(new Robot("Sander", 5, robots.get(robots.size() -1)));

        ExecutorService workerExecutor = Executors.newFixedThreadPool(16);
        robots.forEach(robot -> workerExecutor.execute(robot));

        // Creating a warehouse object, this handles various functionality related to accepting orders (...)
        // (...) from the dealership, as well as passing cars to/from robots on the production line.
        Warehouse warehouse = new Warehouse(
                robots.get(robots.size() -1),
                robots.get(0),
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
        timer.schedule(task, millisecondsPerTic * programDuration);

        // Dealership ordering vehicles starts here
        Dealership dealership = new Dealership(assignment1, warehouse);
        workerExecutor.execute(dealership);
    }

}

class Vehicle {
    Integer uid;
    String model;
    String colour;
    String status;
    Vehicle(String model, String colour, Integer uid) {
        this.uid = uid;
        this.model = model;
        this.colour = colour;
        this.status = "Unpainted";
    }
}

class Warehouse {
    // tics between new cars arriving
    Dictionary<String, AtomicInteger> current_stock;
    Integer limit_per_model;
    private Station placed_orders;
    Queue<Vehicle> carrier_trailer;
    AtomicInteger trailer_counter;
    List<String> car_models;
    Assignment1 assignment1;

    Warehouse(Station first_robot_queue, Queue<Vehicle> trailer, Assignment1 assignment1) {
        this.current_stock = new Hashtable<String, AtomicInteger>();
        assignment1.car_models.forEach(car -> current_stock.put(car, new AtomicInteger(5)));

        this.limit_per_model = assignment1.warehouse_capacity;
        this.placed_orders = first_robot_queue;
        this.carrier_trailer = trailer;
        this.trailer_counter = new AtomicInteger(0);
        this.car_models = assignment1.car_models;
        this.assignment1 = assignment1;
    }

    public synchronized void placeOrder(List<String> car_models, List<String> colours){
        Random rng = new Random(Assignment1.randomSeed);
        String random_model = car_models.get(rng.nextInt(car_models.size()));
        String random_colour = colours.get(rng.nextInt(colours.size()));

        Vehicle vehicle = new Vehicle(random_model, random_colour, OrderCounter.getID());
        // Checking stock in warehouse.
        if (current_stock.get(vehicle.model).get() > 0) {
            current_stock.get(vehicle.model).decrementAndGet();

            Logger.log("Received order for vehicle model " + vehicle.model + ", colour " + vehicle.colour + " => There are " + current_stock.get(vehicle.model).get() + " of this model left in stock.");

            try {
                placed_orders.put(vehicle);
            } catch(InterruptedException e) {}
        }
    }
    public synchronized void checkCarrierTrailer () {
        if (carrier_trailer.size() >= 3) {
            synchronized (carrier_trailer) {
                carrier_trailer.clear();
                carrier_trailer.notify();
            }

            Logger.log("Car carrier trailer departed.");
        }
    }
    public synchronized void addToWarehouse() {
        Random rng = new Random(Assignment1.randomSeed);
        String random_model = car_models.get(rng.nextInt(car_models.size()));
        AtomicInteger currentStock = current_stock.get(random_model);
        if ( currentStock.get() < limit_per_model)
        {
            currentStock.incrementAndGet();

            Logger.log("A model " + random_model + " vehicle is added to the warehouse => " + currentStock +  " vehicles of model " + random_model + " remaining.");
        }
    }
}

class Robot extends Station {
    Integer job_length_tics;
    Station next;

    Robot(String role, Integer job_length_tics, Station next) {
        super(1, role);
        this.job_length_tics = job_length_tics;
        this.next = next;
    }

    protected void doWork(Vehicle v) {
        log("- Thread - " + threadNumber + ": " + "The " + name + " robot is now working on order " + v.uid);

        try { Thread.sleep(Assignment1.millisecondsPerTic * job_length_tics); }
        catch (InterruptedException e) {}

        v.status = name;
        log("- Thread - " + threadNumber + ": " + name + " is done with vehicle " + v.uid + ". moving to next stage: " + next.name + ".");

        // Car moving
        try {
            next.put(v);
        }
        catch (InterruptedException e) {}
    }
}

class Dealership implements Runnable{
    AtomicBoolean continueOrders;
    Warehouse warehouse;
    Assignment1 assignment1;
    Random rng;
    public Dealership(Assignment1 assignment1, Warehouse warehouse) {
        rng = new Random(Assignment1.randomSeed);
        this.continueOrders = assignment1.continueOrders;
        this.warehouse = warehouse;
        this.assignment1 = assignment1;
    }

    @Override
    public void run() {
        // Flag for continue orders is set to false when specified number of tics pass
        while (continueOrders.get()){
            // 1 in 10 chance that an order is placed
            if (rng.nextInt(assignment1.orderInterval) == 0) {
                warehouse.placeOrder(assignment1.car_models, assignment1.colours);

                // Dealership calls the warehouse, asking if the carrier trailer is full yet.
                warehouse.checkCarrierTrailer();
            }
            // Dealership delay between orders
            try { Thread.sleep(Assignment1.millisecondsPerTic); }
            catch (InterruptedException e) {}

            // Dealership calls supplier, asks if more vehicles are ready to be delivered to the warehouse.
            // Random 1 in 7 chance of adding another car to warehouse.
            if (rng.nextInt(8) + 1 == 4) {
                warehouse.addToWarehouse();
            }
        }
    }
}
