import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Main extends Application {
    private CurveTool selectedTool;
    private int selectedThickness;
    private CurveStyle selectedStyle;
    private Color selectedColor;
    private ColorPicker colorPicker;
    private Pane canvas;
    private Random random;
    private ArrayList<CurveSegment> segments;
    private CurveSegment currentSegment;
    private CurveSegment currentFocus;
    private CurvePoint currentPoint;
    private Button pointButton;
    private ArrayList<Button> thicknessButtons;
    private ArrayList<Button> styleButtons;
    private ArrayList<Button> toolButtons;
    private boolean isSaved;
    private MenuItem cutCommand;
    private MenuItem copyCommand;
    private MenuItem pasteCommand;
    private String clipboard;
    private int copyOffset;
    private ArrayList<Label> labels;

    enum CurveTool {
        NONE,
        PEN,
        SELECT,
        ERASE
    }

    enum CurveStyle {
        NORMAL,
        DASHED,
        COMBINED,
        DOTTED
    }

    interface CurvePointBind {
        void onChange(double x, double y);
    }

    class ControlPoint {
        public Line line;
        public Circle control;
        public double offsetX;
        public double offsetY;
        public ArrayList<CurvePointBind> callbacks;

        public ControlPoint(CurvePoint parent) {
            callbacks = new ArrayList<>();
            offsetX = (random.nextInt(30) + 30) * (random.nextBoolean() ? -1 : 1);
            offsetY = (random.nextInt(30) + 30) * (random.nextBoolean() ? -1 : 1);

            if (parent.controlPoints.size() > 0) {
                ControlPoint previous = parent.controlPoints.get(0);
                offsetX = -previous.offsetX;
                offsetY = -previous.offsetY;
            }

            double controlX = parent.point.getCenterX() + offsetX;
            double controlY = parent.point.getCenterY() + offsetY;

            line = new Line();
            line.setStroke(Paint.valueOf("grey"));
            line.setStartX(parent.point.getCenterX());
            line.setStartY(parent.point.getCenterY());
            line.setEndX(controlX);
            line.setEndY(controlY);

            control = new Circle(controlX, controlY, 4);
            control.setFill(Color.valueOf("blue"));

            parent.bind((bindX, bindY) -> {
                line.setStartX(bindX);
                line.setStartY(bindY);
                line.setEndX(bindX + offsetX);
                line.setEndY(bindY + offsetY);
                if (parent.smooth) {
                    updatePosition(bindX + offsetX, bindY + offsetY);
                } else {
                    updatePosition(bindX, bindY);
                }
            });

            control.setOnMouseDragged(event -> {
                if (Main.this.selectedTool == CurveTool.SELECT) {
                    Main.this.isSaved = false;
                    double x = event.getX();
                    double y = event.getY();

                    move(x, y, x - parent.point.getCenterX(), y - parent.point.getCenterY());

                    if (parent.controlPoints.size() == 2) {
                        ControlPoint other = parent.controlPoints.get(0);

                        if (other == this) {
                            other = parent.controlPoints.get(1);
                        }

                        double distance = Math.sqrt(other.offsetX * other.offsetX + other.offsetY * other.offsetY);
                        double angle = Math.atan2(offsetY, offsetX) + Math.PI;
                        double otherOffsetX = distance * Math.cos(angle);
                        double otherOffsetY = distance * Math.sin(angle);

                        other.move(parent.point.getCenterX() + otherOffsetX, parent.point.getCenterY() + otherOffsetY, otherOffsetX, otherOffsetY);
                    }
                }
            });

            control.setOnMousePressed(event -> {
                if (Main.this.selectedTool == CurveTool.SELECT) {
                    Main.this.setPointSelection(parent);
                }
            });
        }

        public void add() {
            Main.this.canvas.getChildren().addAll(line, control);
        }

        public void remove() {
            Main.this.canvas.getChildren().removeAll(line, control);
        }

        public void bind(CurvePointBind callback) {
            callbacks.add(callback);
        }

        public String save() {
            String data = "%";

            data += line.getStartX() + "%";
            data += line.getStartY() + "%";
            data += line.getEndX() + "%";
            data += line.getEndY() + "%";
            data += control.getCenterX() + "%";
            data += control.getCenterY() + "%";
            data += offsetX + "%";
            data += offsetY + "%";

            return data;
        }

        private void move(double x, double y, double offsetX, double offsetY) {
            updatePosition(x, y);

            line.setEndX(x);
            line.setEndY(y);

            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        private void updatePosition(double x, double y) {
            control.setCenterX(x);
            control.setCenterY(y);

            for (CurvePointBind callback : callbacks) {
                callback.onChange(x, y);
            }
        }
    }

    class CurvePoint {
        public Circle point;
        public ArrayList<ControlPoint> controlPoints;
        public ArrayList<CurvePointBind> callbacks;
        public boolean smooth;

        CurvePoint(double x, double y) {
            point = new Circle(x, y, 10);
            point.setFill(Color.valueOf("white"));
            point.setStrokeWidth(3);
            point.setStroke(Color.valueOf("blue"));

            point.setOnMouseDragged(event -> {
                if (Main.this.selectedTool == CurveTool.SELECT) {
                    Main.this.isSaved = false;
                    updatePosition(event.getX(), event.getY());
                }
            });

            point.setOnMousePressed(event -> {
                if (Main.this.selectedTool == CurveTool.SELECT) {
                    Main.this.setPointSelection(this);
                }
            });

            controlPoints = new ArrayList<>();
            callbacks = new ArrayList<>();
            smooth = true;
        }

        public ControlPoint addControlPoint() {
            ControlPoint control = new ControlPoint(this);
            controlPoints.add(control);
            return control;
        }

        public void add() {
            Main.this.canvas.getChildren().add(point);
            if (smooth) {
                for (ControlPoint controlPoint : controlPoints) {
                    controlPoint.add();
                }
            }

        }

        public void remove() {
            Main.this.canvas.getChildren().remove(point);
            if (smooth) {
                for (ControlPoint controlPoint : controlPoints) {
                    controlPoint.remove();
                }
            }
        }

        public void bind(CurvePointBind callback) {
            callbacks.add(callback);
        }

        public void focus() {
            point.setFill(Color.valueOf("lightgreen"));
        }

        public void removeFocus() {
            point.setFill(Color.valueOf("white"));
        }

        public void toggle() {
            smooth = !smooth;
            Main.this.isSaved = false;

            if (smooth) {
                point.setStroke(Color.valueOf("blue"));
            } else {
                point.setStroke(Color.valueOf("green"));
            }

            for (ControlPoint controlPoint : controlPoints) {
                if (smooth) {
                    controlPoint.updatePosition(point.getCenterX() + controlPoint.offsetX, point.getCenterY() + controlPoint.offsetY);
                    controlPoint.add();
                } else {
                    controlPoint.updatePosition(point.getCenterX(), point.getCenterY());
                    controlPoint.remove();
                }
            }
        }

        public String save() {
            StringBuilder data = new StringBuilder(":");

            data.append(point.getCenterX()).append(":");
            data.append(point.getCenterY()).append(":");
            data.append(smooth).append(":");

            for (ControlPoint control: controlPoints) {
                data.append(control.save()).append(":");
            }

            return data.toString();
        }

        private void updatePosition(double x, double y) {
            point.setCenterX(x);
            point.setCenterY(y);

            for (CurvePointBind callback : callbacks) {
                callback.onChange(x, y);
            }
        }
    }

    class CurveSegment {
        public ArrayList<CurvePoint> points;
        public ArrayList<CubicCurve> curves;
        public Color color;
        public CurveStyle style;
        public int thickness;

        public CurveSegment() {
            points = new ArrayList<>();
            curves = new ArrayList<>();
            color = null;
            style = null;
            thickness = 0;
        }

        public CurveSegment(String data) {
            this();

            ArrayList<String> parts = new ArrayList<>(Arrays.asList(data.split("\\|")));
            parts.removeIf(String::isEmpty);

            color = Color.valueOf(parts.get(0));
            style = CurveStyle.valueOf(parts.get(1));
            thickness = Integer.parseInt(parts.get(2));

            for (int i = 3; i < parts.size(); ++i) {
                ArrayList<String> pointData = new ArrayList<>(Arrays.asList(parts.get(i).split(":")));
                pointData.removeIf(String::isEmpty);

                add(Double.parseDouble(pointData.get(0)), Double.parseDouble(pointData.get(1)));
            }

            for (int i = 3; i < parts.size(); ++i) {
                ArrayList<String> pointData = new ArrayList<>(Arrays.asList(parts.get(i).split(":")));
                pointData.removeIf(String::isEmpty);

                CurvePoint current = points.get(i - 3);

                if (!Boolean.parseBoolean(pointData.get(2))) {
                    current.toggle();
                }

                for (int j = 3; j < pointData.size(); ++j) {
                    ArrayList<String> controlData = new ArrayList<>(Arrays.asList(pointData.get(j).split("%")));
                    controlData.removeIf(String::isEmpty);

                    ControlPoint currentControl = current.controlPoints.get(j - 3);

                    currentControl.offsetX = Double.parseDouble(controlData.get(6));
                    currentControl.offsetY = Double.parseDouble(controlData.get(7));
                    currentControl.line.setStartX(Double.parseDouble(controlData.get(0)));
                    currentControl.line.setStartY(Double.parseDouble(controlData.get(1)));
                    currentControl.line.setEndX(Double.parseDouble(controlData.get(2)));
                    currentControl.line.setEndY(Double.parseDouble(controlData.get(3)));
                    currentControl.updatePosition(Double.parseDouble(controlData.get(4)), Double.parseDouble(controlData.get(5)));
                }
            }
        }

        public void add(double x, double y) {
            CurvePoint current = new CurvePoint(x, y);

            if (points.size() > 0) {
                if (this.color == null) {
                    this.color = Main.this.selectedColor;
                    this.style = Main.this.selectedStyle;
                    this.thickness = Main.this.selectedThickness;
                }

                CurvePoint previous = points.get(points.size() - 1);
                ControlPoint previousControl = previous.addControlPoint();
                ControlPoint currentControl = current.addControlPoint();

                CubicCurve cubic = new CubicCurve();
                cubic.setFill(null);
                cubic.setStrokeWidth(this.thickness);
                cubic.setStroke(this.color);
                cubic.setStartX(previous.point.getCenterX());
                cubic.setStartY(previous.point.getCenterY());
                cubic.setControlX1(previousControl.control.getCenterX());
                cubic.setControlY1(previousControl.control.getCenterY());
                cubic.setControlX2(currentControl.control.getCenterX());
                cubic.setControlY2(currentControl.control.getCenterY());
                cubic.setEndX(x);
                cubic.setEndY(y);

                changeLineStyle(cubic);

                previousControl.bind((bindX, bindY) -> {
                    cubic.setControlX1(bindX);
                    cubic.setControlY1(bindY);
                });

                currentControl.bind((bindX, bindY) -> {
                    cubic.setControlX2(bindX);
                    cubic.setControlY2(bindY);
                });

                previous.bind((bindX, bindY) -> {
                    cubic.setStartX(bindX);
                    cubic.setStartY(bindY);
                });

                current.bind((bindX, bindY) -> {
                    cubic.setEndX(bindX);
                    cubic.setEndY(bindY);
                });

                cubic.setOnMouseClicked(event -> {
                    if (Main.this.selectedTool == CurveTool.SELECT) {
                        Main.this.setSelection(this);
                    } else if (Main.this.selectedTool == CurveTool.ERASE) {
                        Main.this.removeCurve(this);
                    }
                });

                Main.this.canvas.getChildren().add(cubic);
                curves.add(cubic);
            }

            points.add(current);
        }

        public void render() {
            for (CurvePoint current : points) {
                current.remove();
                current.add();
            }
        }

        public void refresh() {
            Main.this.isSaved = false;
            for (CubicCurve curve : curves) {
                curve.setStroke(this.color);
                curve.setStrokeWidth(this.thickness);
                changeLineStyle(curve);
            }
        }

        public void focus() {
            for (CurvePoint point : points) {
                point.add();
            }
        }

        public void removeFocus() {
            for (CurvePoint point : points) {
                point.remove();
            }
        }

        public void clear() {
            for (CurvePoint point : points) {
                point.remove();
            }

            for (CubicCurve curve: curves) {
                Main.this.canvas.getChildren().remove(curve);
            }

            curves.clear();
            points.clear();
        }

        public String save() {
            StringBuilder data = new StringBuilder("|");

            data.append(color).append("|");
            data.append(style).append("|");
            data.append(thickness).append("|");

            for (CurvePoint point : points) {
                data.append(point.save()).append("|");
            }

            return data.toString();
        }

        private void changeLineStyle(CubicCurve cubic) {
            cubic.getStrokeDashArray().clear();
            if (this.style == CurveStyle.DOTTED) {
                if (this.thickness >= 15) {
                    cubic.getStrokeDashArray().addAll(2d, 28d);
                } else {
                    cubic.getStrokeDashArray().addAll(2d, 14d);
                }
            } else if (this.style == CurveStyle.DASHED) {
                if (this.thickness >= 15) {
                    cubic.getStrokeDashArray().addAll(25d, 30d);
                } else {
                    cubic.getStrokeDashArray().addAll(25d, 20d);
                }
            } else if (this.style == CurveStyle.COMBINED) {
                if (this.thickness >= 15) {
                    cubic.getStrokeDashArray().addAll(25d, 30d, 5d, 30d);
                } else {
                    cubic.getStrokeDashArray().addAll(25d, 20d, 5d, 20d);
                }
            }
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        selectedTool = CurveTool.NONE;
        selectedThickness = 5;
        selectedStyle = CurveStyle.NORMAL;
        selectedColor = Color.valueOf("black");
        colorPicker = new ColorPicker();
        canvas = new Pane();
        random = new Random();
        segments = new ArrayList<>();
        currentSegment = new CurveSegment();
        currentFocus = null;
        currentPoint = null;
        thicknessButtons = new ArrayList<>();
        styleButtons = new ArrayList<>();
        toolButtons = new ArrayList<>();
        isSaved = true;
        clipboard = null;
        copyOffset = 40;
        labels = new ArrayList<>();

        Menu menuFile = new Menu("File");
        Menu menuEdit = new Menu("Edit");
        Menu menuHelp = new Menu("Help");

        MenuItem newCommand = new MenuItem("New");
        MenuItem loadCommand = new MenuItem("Load");
        MenuItem saveCommand = new MenuItem("Save");
        MenuItem quitCommand = new MenuItem("Quit");
        MenuItem aboutCommand = new MenuItem("About");

        cutCommand = new MenuItem("Cut");
        copyCommand = new MenuItem("Copy");
        pasteCommand = new MenuItem("Paste");

        newCommand.setOnAction(event -> {
            if (selectedTool == CurveTool.PEN) {
                commitSegment();
            } else if (selectedTool == CurveTool.SELECT) {
                clearSelection();
            }
            promptShouldSave(stage);
            for (CurveSegment curve : segments) {
                curve.clear();
            }
            segments.clear();
            isSaved = true;
        });

        loadCommand.setOnAction(event -> {
            if (selectedTool == CurveTool.PEN) {
                commitSegment();
            } else if (selectedTool == CurveTool.SELECT) {
                clearSelection();
            }
            promptShouldSave(stage);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Load Curve");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Curve File (*.curve)", "*.curve"));
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                for (CurveSegment curve : segments) {
                    curve.clear();
                }
                segments.clear();
                loadCurves(file);
                isSaved = true;
            }
        });

        saveCommand.setOnAction(event -> {
            if (selectedTool == CurveTool.PEN) {
                commitSegment();
            } else if (selectedTool == CurveTool.SELECT) {
                clearSelection();
            }
            promptSave(stage);
        });

        quitCommand.setOnAction(event -> {
            promptShouldSave(stage);
            Platform.exit();
        });

        aboutCommand.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Curve Editor");
            alert.setHeaderText("A program created by Terry TX Zheng.");
            alert.setContentText("Computer Science, TT3ZHENG, 20761304");
            alert.showAndWait();
        });

        cutCommand.setOnAction(event -> {
            if (currentFocus != null) {
                copyOffset = 0;
                isSaved = false;
                CurveSegment curve = currentFocus;
                clearSelection();
                clipboard = curve.save();
                curve.clear();
                segments.remove(curve);
                pasteCommand.setDisable(false);
            }
        });

        copyCommand.setOnAction(event -> {
            if (currentFocus != null) {
                copyOffset = 40;
                clipboard = currentFocus.save();
                pasteCommand.setDisable(false);
            }
        });

        pasteCommand.setOnAction(event -> {
            if (clipboard != null) {
                isSaved = false;
                CurveSegment copy = new CurveSegment(clipboard);
                segments.add(copy);
                for (CurvePoint point : copy.points) {
                    point.updatePosition(point.point.getCenterX() + copyOffset, point.point.getCenterY() + copyOffset);
                }
                copyOffset += 40;
            }
        });

        cutCommand.setDisable(true);
        copyCommand.setDisable(true);
        pasteCommand.setDisable(true);

        menuFile.getItems().addAll(newCommand, loadCommand, saveCommand, quitCommand);
        menuEdit.getItems().addAll(cutCommand, copyCommand, pasteCommand);
        menuHelp.getItems().addAll(aboutCommand);

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(menuFile, menuEdit, menuHelp);

        ImageView penImage = new ImageView(new Image("/pen.png", 80, 80, true, true));
        ImageView selectImage = new ImageView(new Image("/select.png", 80, 80, true, true));
        ImageView pointImage = new ImageView(new Image("/point.png", 80, 80, true, true));
        ImageView eraseImage = new ImageView(new Image("/erase.png", 80, 80, true, true));

        pointButton = new Button("", pointImage);
        pointButton.setOnAction(event -> currentPoint.toggle());
        pointButton.setDisable(true);

        Button penButton = new Button("", penImage);
        Button selectButton = new Button("", selectImage);
        Button eraseButton = new Button("", eraseImage);

        penButton.setOnAction(event -> toolChange(CurveTool.PEN));
        selectButton.setOnAction(event -> toolChange(CurveTool.SELECT));
        eraseButton.setOnAction(event -> toolChange(CurveTool.ERASE));

        penButton.setMaxHeight(90);
        penButton.setMinHeight(40);
        penButton.setPrefHeight(90);
        selectButton.setMaxHeight(90);
        selectButton.setMinHeight(40);
        selectButton.setPrefHeight(90);
        eraseButton.setMaxHeight(90);
        eraseButton.setMinHeight(40);
        eraseButton.setPrefHeight(90);
        pointButton.setMaxHeight(90);
        pointButton.setMinHeight(40);
        pointButton.setPrefHeight(90);

        penImage.fitHeightProperty().bind(penButton.heightProperty());
        selectImage.fitHeightProperty().bind(selectButton.heightProperty());
        pointImage.fitHeightProperty().bind(pointButton.heightProperty());
        eraseImage.fitHeightProperty().bind(eraseButton.heightProperty());

        penImage.fitWidthProperty().bind(penButton.heightProperty());
        selectImage.fitWidthProperty().bind(selectButton.heightProperty());
        pointImage.fitWidthProperty().bind(pointButton.heightProperty());
        eraseImage.fitWidthProperty().bind(eraseButton.heightProperty());

        toolButtons.add(penButton);
        toolButtons.add(selectButton);
        toolButtons.add(eraseButton);

        HBox lineOne = new HBox(createHSpacer(), penButton, createHSpacer(), selectButton, createHSpacer());
        HBox lineTwo = new HBox(createHSpacer(), eraseButton, createHSpacer(), pointButton, createHSpacer());

        lineOne.setSpacing(10);
        lineTwo.setSpacing(10);

        VBox toolsContainer = new VBox();
        toolsContainer.setPadding(new Insets(0, 20, 0, 20));
        toolsContainer.setSpacing(20);
        toolsContainer.getChildren().addAll(createVSpacer(), createLabel("Select Tool"), lineOne, lineTwo, createVSpacer());

        colorPicker.setValue(Color.valueOf("Black"));
        colorPicker.setOnAction(event -> colorChange());
        colorPicker.setDisable(true);
        colorPicker.setMaxWidth(1000);
        colorPicker.setMinHeight(25);

        ImageView thicknessOneImage = new ImageView(new Image("/square.png", 5, 100, false, false));
        ImageView thicknessTwoImage = new ImageView(new Image("/square.png", 10, 100, false, false));
        ImageView thicknessThreeImage = new ImageView(new Image("/square.png", 15, 100, false, false));
        ImageView thicknessFourImage = new ImageView(new Image("/square.png", 20, 100, false, false));

        Button thicknessOne = new Button("", thicknessOneImage);
        Button thicknessTwo = new Button("", thicknessTwoImage);
        Button thicknessThree = new Button("", thicknessThreeImage);
        Button thicknessFour = new Button("", thicknessFourImage);

        thicknessOne.setOnAction(event -> thicknessChange(5));
        thicknessTwo.setOnAction(event -> thicknessChange(10));
        thicknessThree.setOnAction(event -> thicknessChange(15));
        thicknessFour.setOnAction(event -> thicknessChange(20));

        thicknessOne.setMaxHeight(120);
        thicknessOne.setMinHeight(20);
        thicknessOne.setPrefHeight(120);
        thicknessTwo.setMaxHeight(120);
        thicknessTwo.setMinHeight(20);
        thicknessTwo.setPrefHeight(120);
        thicknessThree.setMaxHeight(120);
        thicknessThree.setMinHeight(20);
        thicknessThree.setPrefHeight(120);
        thicknessFour.setMaxHeight(120);
        thicknessFour.setMinHeight(20);
        thicknessFour.setPrefHeight(120);

        thicknessOneImage.fitHeightProperty().bind(thicknessOne.heightProperty());
        thicknessTwoImage.fitHeightProperty().bind(thicknessTwo.heightProperty());
        thicknessThreeImage.fitHeightProperty().bind(thicknessThree.heightProperty());
        thicknessFourImage.fitHeightProperty().bind(thicknessFour.heightProperty());

        thicknessButtons.add(thicknessOne);
        thicknessButtons.add(thicknessTwo);
        thicknessButtons.add(thicknessThree);
        thicknessButtons.add(thicknessFour);

        thicknessOne.setDisable(true);
        thicknessTwo.setDisable(true);
        thicknessThree.setDisable(true);
        thicknessFour.setDisable(true);

        HBox thickness = new HBox();
        thickness.getChildren().addAll(createHSpacer(), thicknessOne, createHSpacer(), thicknessTwo, createHSpacer(), thicknessThree, createHSpacer(), thicknessFour, createHSpacer());

        ImageView normalImage = new ImageView(new Image("/square.png", 20, 100, false, false));
        ImageView dashImage = new ImageView(new Image("/dash.png", 20, 100, false, false));
        ImageView combineImage = new ImageView(new Image("/combine.png", 20, 100, false, false));
        ImageView dotImage = new ImageView(new Image("/dot.png", 20, 100, false, false));

        Button normalButton = new Button("", normalImage);
        Button dashButton = new Button("", dashImage);
        Button combineButton = new Button("", combineImage);
        Button dotButton = new Button("", dotImage);

        normalButton.setOnAction(event -> styleChange(CurveStyle.NORMAL));
        dashButton.setOnAction(event -> styleChange(CurveStyle.DASHED));
        combineButton.setOnAction(event -> styleChange(CurveStyle.COMBINED));
        dotButton.setOnAction(event -> styleChange(CurveStyle.DOTTED));

        normalButton.setMaxHeight(120);
        normalButton.setMinHeight(20);
        normalButton.setPrefHeight(120);
        dashButton.setMaxHeight(120);
        dashButton.setMinHeight(20);
        dashButton.setPrefHeight(120);
        combineButton.setMaxHeight(120);
        combineButton.setMinHeight(20);
        combineButton.setPrefHeight(120);
        dotButton.setMaxHeight(120);
        dotButton.setMinHeight(20);
        dotButton.setPrefHeight(120);

        normalImage.fitHeightProperty().bind(normalButton.heightProperty());
        dashImage.fitHeightProperty().bind(dashButton.heightProperty());
        combineImage.fitHeightProperty().bind(combineButton.heightProperty());
        dotImage.fitHeightProperty().bind(dotButton.heightProperty());

        styleButtons.add(normalButton);
        styleButtons.add(dashButton);
        styleButtons.add(combineButton);
        styleButtons.add(dotButton);

        normalButton.setDisable(true);
        dashButton.setDisable(true);
        combineButton.setDisable(true);
        dotButton.setDisable(true);

        HBox style = new HBox();
        style.getChildren().addAll(createHSpacer(), normalButton, createHSpacer(), dashButton, createHSpacer(), combineButton, createHSpacer(), dotButton, createHSpacer());

        VBox properties = new VBox();
        properties.setPadding(new Insets(0, 20, 0, 20));
        properties.setSpacing(20);
        properties.getChildren().addAll(createVSpacer(), createLabel("Select Color"), colorPicker, createLabel("Select Thickness"), thickness, createLabel("Select Style"), style, createVSpacer());

        Region separator = new Region();
        separator.setBorder(new Border(new BorderStroke(Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE, CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY)));

        VBox sideBar = new VBox();
        sideBar.getChildren().addAll(createVSpacer(), toolsContainer, createVSpacer(), separator, createVSpacer(), properties, createVSpacer(), createVSpacer());
        sideBar.setBorder(new Border(new BorderStroke(Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, BorderStrokeStyle.NONE, BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE, BorderStrokeStyle.NONE, CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY)));

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setOnMouseClicked(event -> canvasClick(event.getX(), event.getY()));
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-background-color: transparent;");

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(scrollPane);
        borderPane.setLeft(sideBar);
        borderPane.setTop(menuBar);

        stage.addEventHandler(KeyEvent.KEY_RELEASED, (KeyEvent event) -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (selectedTool == CurveTool.PEN) {
                    commitSegment();
                } else if (selectedTool == CurveTool.SELECT) {
                    clearSelection();
                }
            } else if (event.getCode() == KeyCode.DELETE) {
                if (selectedTool == CurveTool.SELECT && currentFocus != null) {
                    removeCurve(currentFocus);
                }
            }
        });

        stage.heightProperty().addListener(event -> {
            if (stage.getHeight() < 700) {
                properties.setSpacing(10);
                toolsContainer.setSpacing(10);
                for (Label label : labels) {
                    label.setFont(new Font(14.0));
                }
            } else {
                properties.setSpacing(20);
                toolsContainer.setSpacing(20);
                for (Label label : labels) {
                    label.setFont(new Font(18.0));
                }
            }
        });

        stage.setScene(new Scene(borderPane, 1100, 800));
        stage.setMinHeight(480);
        stage.setMinWidth(640);
        stage.setMaxHeight(1200);
        stage.setMaxWidth(1600);
        stage.setTitle("Curve Editor");
        stage.show();
    }

    private void saveCurves(File file) {
        if (file != null) {
            StringBuilder data = new StringBuilder("*");
            for (CurveSegment curve : segments) {
                data.append(curve.save()).append("*");
            }
            try {
                FileWriter writer = new FileWriter(file);
                writer.write(data.toString());
                writer.close();
            } catch (Exception exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("An error has occurred!");
                alert.setContentText(exception.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void loadCurves(File file) {
        if (file != null) {
            try {
                String data = Files.readString(file.toPath());
                ArrayList<String> dataParts = new ArrayList<>(Arrays.asList(data.split("\\*")));
                dataParts.removeIf(String::isEmpty);
                for (String datum : dataParts) {
                    segments.add(new CurveSegment(datum));
                }
            } catch (Exception exception) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("An error has occurred!");
                alert.setContentText(exception.getMessage());
                alert.showAndWait();
            }
        }
    }

    private void commitSegment() {
        if (currentSegment.points.size() >= 2) {
            Main.this.isSaved = false;
            segments.add(currentSegment);
            currentSegment.removeFocus();
            currentSegment = new CurveSegment();
        } else {
            currentSegment.clear();
        }
        if (selectedTool != CurveTool.NONE) {
            toolButtons.get(selectedTool.ordinal() - 1).setStyle("");
        }
        disableProperties();
        selectedTool = CurveTool.NONE;
    }

    private void clearSelection() {
        if (currentFocus != null) {
            disableProperties();
            currentFocus.removeFocus();
            currentFocus = null;
        }
        removePointSelection();
        cutCommand.setDisable(true);
        copyCommand.setDisable(true);
    }

    private void setSelection(CurveSegment segment) {
        if (currentFocus != segment) {
            clearSelection();
            currentFocus = segment;
            currentFocus.focus();
            thicknessChange(currentFocus.thickness);
            styleChange(currentFocus.style);
            selectedColor = currentFocus.color;
            colorPicker.setValue(selectedColor);
            enableProperties();
            cutCommand.setDisable(false);
            copyCommand.setDisable(false);
        }
    }

    private void setPointSelection(CurvePoint point) {
        if (currentPoint != point) {
            removePointSelection();
            currentPoint = point;
            currentPoint.focus();
            pointButton.setDisable(false);
        }
    }

    private void removePointSelection() {
        if (currentPoint != null) {
            currentPoint.removeFocus();
            currentPoint = null;
            pointButton.setDisable(true);
        }
    }

    private void removeCurve(CurveSegment segment) {
        clearSelection();
        segment.clear();
        segments.remove(segment);
        isSaved = false;
    }

    private void promptShouldSave(Stage stage) {
        if (!isSaved) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Would you like to save your work?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait();
            if (alert.getResult() == ButtonType.YES) {
                promptSave(stage);
            }
        }
    }

    private void promptSave(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save As");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Curve File (*.curve)", "*.curve"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            isSaved = true;
        }
        saveCurves(file);
    }

    private void toolChange(CurveTool tool) {
        if (selectedTool == CurveTool.PEN) {
            commitSegment();
        } else if (selectedTool == CurveTool.SELECT) {
            clearSelection();
        }
        if (selectedTool != CurveTool.NONE) {
            toolButtons.get(selectedTool.ordinal() - 1).setStyle("");
        }
        toolButtons.get(tool.ordinal() - 1).setStyle("-fx-background-color: lightgreen;");
        selectedTool = tool;
        if (selectedTool == CurveTool.PEN) {
            enableProperties();
        } else {
            disableProperties();
        }
    }

    private void enableProperties() {
        colorPicker.setDisable(false);
        for (Button button : thicknessButtons) {
            button.setDisable(false);
        }
        for (Button button : styleButtons) {
            button.setDisable(false);
        }
        styleButtons.get(selectedStyle.ordinal()).setStyle("-fx-background-color: lightgreen;");
        thicknessButtons.get(selectedThickness / 5 - 1).setStyle("-fx-background-color: lightgreen;");
    }

    private void disableProperties() {
        colorPicker.setDisable(true);
        for (Button button : thicknessButtons) {
            button.setDisable(true);
        }
        for (Button button : styleButtons) {
            button.setDisable(true);
        }
        styleButtons.get(selectedStyle.ordinal()).setStyle("");
        thicknessButtons.get(selectedThickness / 5 - 1).setStyle("");
    }

    private void colorChange() {
        if (selectedTool == CurveTool.PEN) {
            commitSegment();
            selectedTool = CurveTool.PEN;
            enableProperties();
            toolButtons.get(0).setStyle("-fx-background-color: lightgreen;");
        }
        selectedColor = colorPicker.getValue();
        if (currentFocus != null && selectedTool == CurveTool.SELECT) {
            if (currentFocus.color != selectedColor) {
                currentFocus.color = selectedColor;
                currentFocus.refresh();
            }
        }
    }

    private void styleChange(CurveStyle style) {
        if (selectedTool == CurveTool.PEN) {
            commitSegment();
            selectedTool = CurveTool.PEN;
            enableProperties();
            toolButtons.get(0).setStyle("-fx-background-color: lightgreen;");
        }
        styleButtons.get(selectedStyle.ordinal()).setStyle("");
        styleButtons.get(style.ordinal()).setStyle("-fx-background-color: lightgreen;");
        selectedStyle = style;
        if (currentFocus != null && selectedTool == CurveTool.SELECT) {
            if (currentFocus.style != selectedStyle) {
                currentFocus.style = selectedStyle;
                currentFocus.refresh();
            }
        }
    }

    private void thicknessChange(int value) {
        if (selectedTool == CurveTool.PEN) {
            commitSegment();
            selectedTool = CurveTool.PEN;
            enableProperties();
            toolButtons.get(0).setStyle("-fx-background-color: lightgreen;");
        }
        thicknessButtons.get(selectedThickness / 5 - 1).setStyle("");
        thicknessButtons.get(value / 5 - 1).setStyle("-fx-background-color: lightgreen;");
        selectedThickness = value;
        if (currentFocus != null && selectedTool == CurveTool.SELECT) {
            if (currentFocus.thickness != value) {
                currentFocus.thickness = value;
                currentFocus.refresh();
            }
        }
    }

    private void canvasClick(double x, double y) {
        if (selectedTool == CurveTool.PEN) {
            currentSegment.add(x, y);
            currentSegment.render();
        }
    }

    private Label createLabel(String text) {
        Label label = new Label(text);
        label.setFont(new Font(18.0));
        labels.add(label);
        return label;
    }

    private Region createHSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Region createVSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
