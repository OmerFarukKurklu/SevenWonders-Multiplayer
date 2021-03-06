/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package model;

//import javafx.event.Event;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
//@Author:Akin_Parkan

public class Table {
    private final int MAGIC_CARD_NUMBER = 4;
    private final int DICE_ENTRY_PRICE = 1;
    private final String DICE_RESULT_MAGIC_CARD_IDENTIFIER = "magic card";
    
    private String tableID;
    private int noOfPlayers;
    private int age;
    private int turn;
    private int noOfActions; // how many people played in that turn
    private Deck age1Deck;
    private HashMap<String, Boolean> isActionLocked = new HashMap<>();
    private Deck age2Deck;
    private Deck age3Deck;
    private Deck magicCardDeck;
    private ArrayList<String> wonderNames;
    private List<Card> discardedCards;
    private Card[][] hand;
    private List<WonderBoard> diceRollers = new LinkedList<>();
    private String diceRollWinner;
    private  MagicCard diceRollCard;
    private ScoreBoard scoreboard;
    private LinkedBlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
    private Map<String, Socket> playerChannel = new HashMap<>();
    private HashMap<String, WonderBoard> wonders;
    private String owner;
    private boolean rollDice;
    private List<String> playerIDs;
    private HandContainer trans;
    private TableNotifier notifier;
    private Card[] playable1;
    private Card[] playable2;
    private Card[] playable3;
    private Card[] magicCards;
    private HashMap<String, WonderBoard> wonderboardObjects;
    private int diceRollRequestNumber;
    //private DeckFactory deckFactory;
    //private TableNotifier notifier

    public Table(String tableID, String owner, Deck age1Deck, Deck age2Deck, Deck age3Deck, Deck magicCardDeck, HashMap<String, WonderBoard> wonderboardObjects) {
        wonderNames = new ArrayList<>();
        wonderNames.add("TheColossusOfRhodes");
        wonderNames.add("TheLighthouseOfAlexandria");
        wonderNames.add("TheTempleOfArtemisInEphesus");
        wonderNames.add("TheHangingGardensOfBabylon");
        wonderNames.add("TheStatueOfZeusInOlympia");
        wonderNames.add("TheMausoleumOfHalicarnassus");
        wonderNames.add("ThePyramidsOfGiza");

        this.tableID = tableID;
        this.noOfPlayers = 1;
        this.owner = owner;
        this.wonderboardObjects = wonderboardObjects;
        this.diceRollRequestNumber = 0;
        age = 1;
        playerIDs = new ArrayList<>();
        playerIDs.add(owner);
        
        wonders = new HashMap<>();
        trans = new HandContainer();
        scoreboard = new ScoreBoard(wonders);
        notifier = new TableNotifier(eventQueue, playerChannel);
        
        this.age1Deck = age1Deck;
        this.age2Deck = age2Deck;
        this.magicCardDeck = magicCardDeck;
        this.age3Deck = age3Deck;

        //hand initialize edilecek
    }

    public void playerJoined(String playerID, Socket socket) throws Exception {
        if (noOfPlayers < 7) {
            if (playerIDs.contains(playerID)) {
                System.out.println("hataa");
                throw new Exception("playerID is taken ");
            } else {
                noOfPlayers++;
                playerIDs.add(playerID);
                eventQueue.offer(Event.PLAYER_JOINED);
            }
        } else {
            throw new Exception("room is full");
        }
    }

    public void init(int noOfPlayer, Deck[] decks) {
        int hold = noOfPlayer * 7;
        playable1 = new Card[hold];
        playable1 = decks[0].prepareCards(noOfPlayer);
        playable2 = new Card[noOfPlayer *7];
        playable2 = decks[1].prepareCards(noOfPlayers);
        magicCards = new Card[MAGIC_CARD_NUMBER];
        magicCards = magicCardDeck.prepareCards(noOfPlayers);
        playable3 = new Card[noOfPlayer *7];
        playable3 = decks[2].prepareCards(noOfPlayers);
    }

    public void startTable() throws CloneNotSupportedException {
        addWonder();
        //assign neighbours
        for (int a = 0; a < noOfPlayers - 1; a++) {
            WonderBoard wb1 = wonders.get(playerIDs.get(a));
            WonderBoard wb2 = wonders.get(playerIDs.get(a + 1));

            wb1.setLeftNeighbor(wb2.getName());
            wb2.setRightNeighbor(wb1.getName());

        }
        WonderBoard wb1 = wonders.get(playerIDs.get(0));
        WonderBoard wb2 = wonders.get(playerIDs.get(noOfPlayers - 1));
        wb2.setLeftNeighbor(wb1.getName());
        wb1.setRightNeighbor(wb2.getName());
        System.out.println(wb1);
        Deck[] decks = new Deck[3];
        decks[0] = this.age1Deck;
        decks[1] = this.age2Deck;
        decks[2] = this.age3Deck;
        init(noOfPlayers, decks);
        hand = new Card[noOfPlayers][7];
        //continue to hand distrib
        List<Card> shuffle1 = Arrays.asList(playable1);
        List<Card> shuffle2 = Arrays.asList(playable2);
        List<Card> shuffle3 = Arrays.asList(playable3);
        List<Card> shuffleMagic = Arrays.asList(magicCards);
        
        Collections.shuffle(shuffle1);
        Collections.shuffle(shuffle2);
        Collections.shuffle(shuffle3);
        
        shuffle1.toArray(playable1);
        shuffle2.toArray(playable2);
        shuffle3.toArray(playable3);
        shuffleMagic.toArray(magicCards);
        int count = -1;
        int hold = 0;
        for (int a = 0; a < noOfPlayers * 7; a++) {
            if (a % 7 == 0 || hold == 7) {
                count++;
                hold = 0;
            }
            hand[count][hold++] = playable1[a];
        }
        //initialize isActionLocked map with for each key = playerId and value = FALSE
        for(Map.Entry<String, WonderBoard> entry : getWonders().entrySet()) {
            isActionLocked.put(entry.getKey(), Boolean.FALSE);
        }
        eventQueue.offer(Event.TABLE_START);
    }
    
    public boolean addToDiceRollers(String wonderID, String isJoined) {
        WonderBoard wb = wonders.get(wonderID);
        if(!diceRollers.contains(wb) && wb != null && isJoined.equals(Boolean.TRUE.toString())) {
            diceRollers.add(wb);
            diceRollRequestNumber++;
            if(wb.getSources().get("coin") < DICE_ENTRY_PRICE)
                return false;
            wb.getSources().put("coin", wb.getSources().get("coin") - DICE_ENTRY_PRICE );
            wb.refactorStrings();
            
            eventQueue.offer(Event.DICE_ROLL_PLAYER_JOINED);
            
            if(diceRollRequestNumber == noOfPlayers) {
                rollDice();
                eventQueue.offer(Event.DICE_ROLL_OVER);
                diceRollRequestNumber = 0;
//                wonders.get(diceRollWinner).
            }
            
            return true;
        } else {
            diceRollRequestNumber++;
            
            if(diceRollRequestNumber == noOfPlayers) {
                rollDice();
                eventQueue.offer(Event.DICE_ROLL_OVER);
                diceRollRequestNumber = 0;
//                wonders.get(diceRollWinner).
            }
            
            return false;
        }
    }
//    public void rollDice(List<WonderBoard> diceRollers) {
//        int highest = -1;
//        List<WonderBoard> sameRolls = new ArrayList<WonderBoard>();
//        for (WonderBoard wb : this.diceRollers){
//            int roll = (int)(Math.random()*7) + (int)(Math.random()*7);
//            wb.setDiceValue(roll);
//            if (roll > highest) {
//                highest = roll;
//                sameRolls.clear();
//                sameRolls.add(wb);
//            }
//            if (roll == highest) {
//                sameRolls.add(wb);
//            }
//        }
//        if (sameRolls.size() >1) {
//            this.rollDice(sameRolls);
//        }
//        else {
//            this.pickMagicCard(sameRolls.get(0));
//            this.diceRollWinner = sameRolls.get(0).getName();
//            //TODO notifyPlayers();
//            eventQueue.offer(Event.DICE_ROLL_OVER);
//        }
//    }
    
    public void rollDice() {

        if(diceRollers != null) {
            int hold = diceRollers.size();
            if(hold > 0)
            {
                int winner =  (int)(Math.random()*((hold -0)))+0;
                this.diceRollCard = pickMagicCard();
                this.diceRollWinner = diceRollers.get(winner).getName();
            //TODO notifyPlayers();
                eventQueue.offer(Event.DICE_ROLL_OVER);
                diceRollers.clear();
            }
        }
    }

    public void addWonder() throws CloneNotSupportedException {
        //assign players to wonders
        String randomWonderName;
        WonderBoard toPut;
        for (int a = 0; a < noOfPlayers; a++) {
            randomWonderName = wonderNames.remove(new Random().nextInt(wonderNames.size()));
            //wonders.put(playerIDs.get(a), new WonderBoard(playerIDs.get(a), a, wonderNames.get(a)));
            toPut = wonderboardObjects.get(randomWonderName).copy();
            toPut.setName(playerIDs.get(a));
            toPut.setHandNo(a);
            
            wonders.put(playerIDs.get(a), toPut);
        }

    }

    public MagicCard pickMagicCard() {
        Integer random = new Random().nextInt(MAGIC_CARD_NUMBER);
        return (MagicCard) magicCards[random];
    }

    public void changeHand() {
    }

    public boolean diceRollRequest(String wonderID) {
        if (diceRollers.contains(this.wonders.get(wonderID))){
            return false;
        }
        WonderBoard wb = this.wonders.get(wonderID);
        HashMap<String, Integer> sources = wb.getSources();
        if (sources.get("coin") < this.DICE_ENTRY_PRICE){
            return false;
        }
        sources.replace("coin",(wb.getSources().get("coin") - this.DICE_ENTRY_PRICE));
        wb.setSources(sources);
        this.diceRollers.add(this.wonders.get(wonderID));
        return true;
    }

    public void notifyPlayers() {
    }

    public void playAge() throws Exception {
        turn = 0;
        //calculate military tokens
        for (Map.Entry<String, WonderBoard> entry : wonders.entrySet()) {
            WonderBoard middle = entry.getValue();
            WonderBoard right = wonders.get(middle.getRightNeighbor());
            WonderBoard left = wonders.get(middle.getLeftNeighbor());

            if (age == 1) {
                if (middle.getSources().get("zshield") > left.getSources().get("zshield")) {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() + 1);
                } else if (middle.getSources().get("zshield") == left.getSources().get("zshield")) {
                } else {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
                }

                if (middle.getSources().get("zshield") > right.getSources().get("zshield")) {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() + 1);
                } else if (middle.getSources().get("zshield") == right.getSources().get("zshield")) {
                } else {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
                }
            } else if (age == 2) {
                if (middle.getSources().get("zshield") > left.getSources().get("zshield")) {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() + 3);
                } else if (middle.getSources().get("zshield") == left.getSources().get("zshield")) {
                } else {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
                }

                if (middle.getSources().get("zshield") > right.getSources().get("zshield")) {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() + 3);
                } else if (middle.getSources().get("zshield") == right.getSources().get("zshield")) {
                } else {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
                }
            } else if (age == 3) {
                if (middle.getSources().get("zshield") > left.getSources().get("zshield")) {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() + 5);
                } else if (middle.getSources().get("zshield") == left.getSources().get("zshield")) {
                } else {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
                }

                if (middle.getSources().get("zshield") > right.getSources().get("zshield")) {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() + 5);
                } else if (middle.getSources().get("zshield") == right.getSources().get("zshield")) {
                } else {
                    middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
                }
            } else {
                throw new Exception("End Age Error");

            }

        }

        if (age == 3) {
            scoreboard.calculateScores();
        } else {
            age++;
            // deal current deck
            if (age == 2) {
                int count = -1;
                int hold = 0;
                for (int a = 0; a < noOfPlayers * 7; a++) {
                    if (a % 7 == 0 || hold == 7) {
                        count++;
                        hold = 0;
                    }
                    hand[count][hold++] = playable2[a];
                }
            }
            if (age == 3) {
                int count = -1;
                int hold = 0;
                for (int a = 0; a < noOfPlayers * 7; a++) {
                    if (a % 7 == 0 || hold == 7) {
                        count++;
                        hold = 0;
                    }
                    hand[count][hold++] = playable3[a];
                }
//            }
            }
            //change deck

            
        }
        eventQueue.offer(Event.AGE_OVER);
    }

    public void lockAction(CardAction action) throws Exception {
        this.getWonders().get(action.getWonderID()).setLockedAction(action);
        if(!isActionLocked.get(action.getWonderID())) {
            isActionLocked.put(action.getWonderID(), Boolean.TRUE);
            noOfActions++;
        }
        if (noOfActions == noOfPlayers) {
            playTurn();
        }
    }

    public boolean isPossible(CardAction action) {
        WonderBoard wb = this.getWonders().get(action.getWonderID());
        List<String> wbSources = wb.getSourcesToCalculate();
        HashMap<String, Integer> leftTrade = action.getLeftTrade();
        HashMap<String, Integer> rightTrade = action.getRightTrade();

        int choice = action.getChoice();
        if (choice == 0) { // Discard is always possible
            return true;
        } else {

            //**//
            ListIterator it = wbSources.listIterator();
            List<String> copy = new LinkedList<>();
            while (it.hasNext()) {
                copy.add((String) it.next());
            }

            //
            HashMap<String, Integer> leftDiscount = wb.getLeftDiscount();
            HashMap<String, Integer> rightDiscount = wb.getRightDiscount();
            int requiredCoin = 0;

            if (leftTrade != null) {
                ListIterator iter = null;
                for (Map.Entry<String, Integer> entry : leftTrade.entrySet()) {
                    iter = copy.listIterator();
                    requiredCoin += entry.getValue() * leftDiscount.get(entry.getKey());

                    if (entry.getValue() != 0) {
                        while (iter.hasNext()) {
                            String tmp = (String) iter.next();
                            for (int i = 0; i < entry.getValue(); i++) {
                                tmp += entry.getKey().charAt(0);
                            }

                            char tempArray[] = tmp.toCharArray();
                            Arrays.sort(tempArray);
                            iter.set(new String(tempArray));
                        }
                    }
                }
            }

            if (rightTrade != null) {
                ListIterator iter = null;
                for (Map.Entry<String, Integer> entry : rightTrade.entrySet()) {
                    iter = copy.listIterator();
                    requiredCoin += entry.getValue() * rightDiscount.get(entry.getKey());

                    if (entry.getValue() != 0) {
                        while (iter.hasNext()) {
                            String tmp = (String) iter.next();
                            for (int i = 0; i < entry.getValue(); i++) {
                                tmp += entry.getKey().charAt(0);
                            }

                            char tempArray[] = tmp.toCharArray();
                            Arrays.sort(tempArray);
                            iter.set(new String(tempArray));
                        }
                    }
                }
            }

            if (requiredCoin > wb.getSources().get("coin")) {
                return false;
            }
            //
            //**//
            if (choice == 1) {  // Card building requires checking
                HashMap<String, Card> builtCards = wb.getBuiltCards();
                Card card = this.getHands()[wb.getHandNo()][action.getCardNo()];
                if (builtCards.containsKey(card.getName())) { // If the card is already built, return false.
                    return false;
                }
//            if (builtCards.containsKey(card.getFreeBuildings())) { // If the free building exist in the Wonder, return true.
//                return true;
//            }
                if (card.getFreeBuildings() != null) {
                    for (String freebuilding : card.getFreeBuildings()) {
                        if (builtCards.containsKey(freebuilding)) { // If the free building exist in the Wonder, return true.
                            return true;
                        }
                    }
                }
                Cost cost = card.getCost();

                //return false if trades are not null and card is still playable with existing sources
                if ((leftTrade != null || rightTrade != null) && costCheck(cost, wbSources, leftTrade, rightTrade)) {
                    return false;
                }

                return costCheck(cost, copy, leftTrade, rightTrade);

            }
            if (choice == 2) {  // Wonder Stage building requires cost checking
                if (wb.getCurrentStage() == 3) {
                    return false;
                }
                Cost cost = wb.getStageCosts()[wb.getCurrentStage()];

                if (costCheck(cost, copy, leftTrade, rightTrade)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    public boolean costCheck(Cost cost, List<String> wbSourcesToCalculate, HashMap<String, Integer> leftTrade, HashMap<String, Integer> rightTrade) {
//        HashMap<String, Integer> costs = cost.getCost();
//        if(!(cost.getCost().keySet().containsAll(leftTrade.keySet()) ) || !(cost.getCost().keySet().containsAll(rightTrade.keySet()) ) ) {
//            return false;
//        }
//        for (String material : costs.keySet()){
//            // If the Wonder already has materials for the card, The Trades map for that material should be zero or key should not exist.
//            if ((wbSources.get(material) >= costs.get(material))) {
//                if ((leftTrade.containsKey(material) && (leftTrade.get(material) != 0)) ||
//                (rightTrade.containsKey(material) && (rightTrade.get(material) != 0))) {
//                    return false;
//                }
//            }
//            // If the Wonder does not have enough materials, its materials plus the trade materials should equal to cost.
//            else {
//                if(leftTrade.containsKey(material) && rightTrade.containsKey(material)){
//                    if (wbSources.get(material) + leftTrade.get(material) + rightTrade.get(material) != costs.get(material)){
//                        return false;
//                    }
//                }
//                else if(leftTrade.containsKey(material)){
//                    if ((wbSources.get(material) + leftTrade.get(material)) != costs.get(material)) {
//                        return false;
//                    }
//                }
//                else if(rightTrade.containsKey(material)){
//                    if ((wbSources.get(material) + rightTrade.get(material)) != costs.get(material)) {
//                        return false;
//                    }
//                }
//                else {
//                    return false;
//                }
//            }
//        }
//        return true;

        ListIterator it = wbSourcesToCalculate.listIterator();
        String sourceToCheck;

        while (it.hasNext()) {
            sourceToCheck = (String) it.next();
            int sourceFoundIndex = 0;

            for (Map.Entry<String, Integer> entry : cost.getCost().entrySet()) {
                if (entry.getValue() != 0) {
                    String tmpToSearch = "";
                    for (int i = 0; i < entry.getValue(); i++) {
                        tmpToSearch += entry.getKey().charAt(0);
                    }
                    if (!sourceToCheck.contains(tmpToSearch)) {
                        break;
                    }
                }
                sourceFoundIndex++;
            }

            if (sourceFoundIndex == cost.getCost().entrySet().size()) {
                return true;
            }
        }
        return false;
    }

//    public boolean areAllTrue(boolean[] array) {
//        for (boolean b : array) {
//            if (!b) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    
//    private String generateCostString(Cost cost) {
//        String costString = "";
//        for(Map.Entry<String, Integer> entry : cost.getCost().entrySet()) {
//            if(entry.getValue() != 0) {
//                for(int i = 0; i < entry.getValue();i++) {
//                    costString += entry.getKey().charAt(0);
//                }
//            }
//        }
//        char tempArray[] = costString.toCharArray();
//        Arrays.sort(tempArray);
//        return new String(tempArray);
//    }
    public void playTurn() throws Exception {
        HashMap<String, WonderBoard> wonders = this.getWonders();
        for (String wbID : wonders.keySet()) {
            this.playAction(wonders.get(wbID));
            //wonders.get(wbID).setHandNo((wonders.get(wbID).getHandNo() + 1)%noOfPlayers);
        }
        turn++;
        noOfActions = 0;
        for (Map.Entry<String, Boolean> entry : isActionLocked.entrySet()) {
            isActionLocked.replace(entry.getKey(), Boolean.FALSE);
        }
        
        if (turn < 6) {
            Card[][] tmp = getHands();
            Card[] tmp2 = tmp[noOfPlayers - 1];
            for (int i = noOfPlayers - 1; i > 0; i--) {
                tmp[i] = tmp[i - 1];
            }
            tmp[0] = tmp2;
        } else {
//            turn = 0;
            //calculate military tokens
//            for (Map.Entry<String, WonderBoard> entry : wonders.entrySet()) {
//                WonderBoard middle = entry.getValue();
//                WonderBoard right = wonders.get(middle.getRightNeighbor());
//                WonderBoard left = wonders.get(middle.getLeftNeighbor());
//
//                if (age == 1) {
//                    if (middle.getSources().get("zshield") > left.getSources().get("zshield")) {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() + 1);
//                    } else if (middle.getSources().get("zshield") == left.getSources().get("zshield")) {
//                    } else {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
//                    }
//
//                    if (middle.getSources().get("zshield") > right.getSources().get("zshield")) {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() + 1);
//                    } else if (middle.getSources().get("zshield") == right.getSources().get("zshield")) {
//                    } else {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
//                    }
//                } else if (age == 2) {
//                    if (middle.getSources().get("zshield") > left.getSources().get("zshield")) {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() + 3);
//                    } else if (middle.getSources().get("zshield") == left.getSources().get("zshield")) {
//                    } else {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
//                    }
//
//                    if (middle.getSources().get("zshield") > right.getSources().get("zshield")) {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() + 3);
//                    } else if (middle.getSources().get("zshield") == right.getSources().get("zshield")) {
//                    } else {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
//                    }
//                } else if (age == 3) {
//                    if (middle.getSources().get("zshield") > left.getSources().get("zshield")) {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() + 5);
//                    } else if (middle.getSources().get("zshield") == left.getSources().get("zshield")) {
//                    } else {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
//                    }
//
//                    if (middle.getSources().get("zshield") > right.getSources().get("zshield")) {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() + 5);
//                    } else if (middle.getSources().get("zshield") == right.getSources().get("zshield")) {
//                    } else {
//                        middle.setMilitaryTokens(middle.getMilitaryTokens() - 1);
//                    }
//                } else {
//                    throw new Exception("End Age Error");
//
//                }
//
//            }
//
//            if (age == 3) {
//                scoreboard.calculateScores();
//            } else {
//                age++;
//            }
//        }
            playAge();
            
        }
        
        for(Map.Entry<String, WonderBoard> entry : this.wonders.entrySet()) {
            entry.getValue().setDiceValue(0);
        }
        diceRollCard = null;
        eventQueue.offer(Event.TURN_OVER);
    }

    public String getTableID() {
        return tableID;
    }

    public void setTableID(String tableID) {
        this.tableID = tableID;
    }

    public int getNoOfPlayers() {
        return noOfPlayers;
    }

    public void setNoOfPlayers(int noOfPlayers) {
        this.noOfPlayers = noOfPlayers;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    private void playAction(WonderBoard wb) {
        CardAction action = wb.getLockedAction();
        int choice = action.getChoice();

        // Get the hands, handNo of Wonder and the the played Card.
        Card[][] hands = this.getHands();
        int wbHandNo = wb.getHandNo();
        int cardNo = action.getCardNo();

        // Discard
        if (choice == 0) {
            // Update te sources of the WonderBoard.
            HashMap<String, Integer> wbSources = wb.getSources();
            wbSources.put("coin", wbSources.get("coin") + 3);
            wb.setSources(wbSources);
            Card card = hands[wbHandNo][cardNo];
            card.play(wb, "0", wonders);
        }

        // Build Card
        if (choice == 1) {
            // Get the card from hand.
            Card card = hands[wbHandNo][cardNo];
            //*ToTest*//
            int requiredCoins = 0;
            if (action.getLeftTrade() != null) {
                for (Map.Entry<String, Integer> entry : action.getLeftTrade().entrySet()) {

                    requiredCoins += action.getLeftTrade().get(entry.getKey()) * wb.getLeftDiscount().get(entry.getKey());

                }
            }

            if (action.getRightTrade() != null) {
                for (Map.Entry<String, Integer> entry : action.getRightTrade().entrySet()) {

                    requiredCoins += action.getRightTrade().get(entry.getKey()) * wb.getRightDiscount().get(entry.getKey());

                }
            }
            ///change at 21.12.2019 18.58 //
            if(card.getCost().getCost().get("coin") != null)
                requiredCoins += card.getCost().getCost().get("coin");
            ///end of change at 21.12.2019 18.58//
            
            ListIterator it = wb.getSourcesToCalculate().listIterator();
            while (it.hasNext()) {
                String tmp = (String) it.next();
                for (int i = 0; i < requiredCoins; i++) {
                    tmp = tmp.replaceFirst("c", "");
                }
                it.set(tmp);
            }
            wb.getSources().put("coin", wb.getSources().get("coin") - requiredCoins);
            //SimpleCard card = (SimpleCard) hands[wbHandNo][cardNo];
            card.play(wb, "", wonders);
            wb.getBuiltCards().put(card.getName(), card);
        }

        // Build Wonder stage. For this iteration our stages only give +3 VP, 0 VP, +7 VP.
        if (choice == 2) {
            //int currentStage = wb.getCurrentStage() + 1;
            //wb.setCurrentStage(currentStage);
            HashMap<String, Integer> wbSources = wb.getSources();
            wb.buildStage(wb.getCurrentStage()+1);
            wb.setSources(wbSources);
        }
        
        // Remove the card from the hand and update hands.
        hands[wbHandNo][cardNo] = null;
        this.setHand(hands);
    }

    public void addChannel(String user, Socket s) {
        playerChannel.put(user, s);
    }

    public Card[][] getHands() {
        return hand;
    }

    public void setHand(Card[][] hand) {
        this.hand = hand;
    }

    public List<WonderBoard> getDiceRollers() {
        return diceRollers;
    }
    
    public HashMap<String, String> getDiceRollersMap() {
        if(diceRollers != null) {
            HashMap<String, String> result = new HashMap<>();
            Iterator it = diceRollers.listIterator();
            
            while(it.hasNext()) {
                WonderBoard tmp = (WonderBoard) it.next();
                result.put(tmp.getName(), tmp.getWonderName());
            }
            return result;
        } else {
            return null;
        }
    }
    
    public HashMap<String, String> getDiceResultMap() {
        HashMap<String, String> result = new HashMap<>();
        
//        if(diceRollers != null) {
//            Iterator it = diceRollers.listIterator();
//            while(it.hasNext()) {
//                String tmpUserID = (String) it.next();
//                result.put(wonders.get(tmpUserID).getName(), Integer.toString(wonders.get(tmpUserID).getDiceValue()));
//            }
//            
//            result.put(DICE_RESULT_MAGIC_CARD_IDENTIFIER, "ahmet");
//            return result;
//        } else {
//            return null;
//        }

        result.put("winner", this.diceRollWinner);
        result.put("magicCard", diceRollCard == null ? null : diceRollCard.toString());
        
        return result;
    }
    
    public void setDiceRollers(List<WonderBoard> diceRollers) {
        this.diceRollers = diceRollers;
    }
    
    
    
    public ScoreBoard getScoreboard() {
        return scoreboard;
    }

    public HashMap<String, WonderBoard> getWonders() {
        return wonders;
    }

    public HandContainer getTransfer() {
        trans = new HandContainer(hand, noOfPlayers, playerIDs);
        return trans;
    }

    public HashMap<String, Integer> getMilitaryPointsTransfer() {
        HashMap<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, WonderBoard> entry : wonders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getMilitaryTokens());
        }
        return result;
    }
    
    public void playMagicCard(String userID) {
        diceRollCard.play(wonders.get(userID), "0", wonders);
    }
}
