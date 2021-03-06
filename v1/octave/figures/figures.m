clear all
close all
groupPrefix = 'sixPrivate';
i = 1;

xPredicted = csvread(strcat(groupPrefix, num2str(i), '-testX.predicted'));
yPredicted = csvread(strcat(groupPrefix, num2str(i), '-testY.predicted'));
cPredicted = csvread(strcat(groupPrefix, num2str(i), '-testC.predicted'));
xActual = csvread(strcat(groupPrefix, num2str(i), '-testX.actual'));
yActual = csvread(strcat(groupPrefix, num2str(i), '-testY.actual'));
cActual = csvread(strcat(groupPrefix, num2str(i), '-testC.actual'));
%trainCActual = csvread(strcat(groupPrefix, num2str(i), '-trainC.actual'));
sensors = csvread(strcat(groupPrefix, num2str(i), '-testC.sensors'));


% Walk distribution figure
figure
axis([-1, 1 -1 1]);
h = scatter(xActual(1:971), yActual(1:971),'r');
grid on
box on
xlabel('X','FontSize',15,'FontWeight','bold'),ylabel('Y','FontSize',15,'FontWeight','bold')

%figure
%axis([-1, 1 -1 1]);
%scatter(xActual, yActual, 'r','linewidth',1);
%xlabel('X','FontSize',15,'FontWeight','bold'),ylabel('Y','FontSize',15,'FontWeight','bold')
%print('test_distrib', '-depsc');

tStart = 200;
tEnd   = 500;

% X Actual & Predicted over time figure
figure
hold on;
axis([0 (tEnd - tStart) -1 1]);
plot(xPredicted(tStart:tEnd), 'Color', 'r','LineWidth',2);
plot(xActual(tStart:tEnd), 'Color', 'b','LineWidth',2);
xlabel('Frame','FontSize',15,'FontWeight','bold'),ylabel('X','FontSize',15,'FontWeight','bold')
AX = legend('Estimates','Ground Truth','FontSize',20,'FontWeight','bold')
LEG = findobj(AX,'type','text');
set(LEG,'FontSize',15,'FontWeight','bold')
hold off;


% Y Actual & Predicted over time figure
figure
hold on;
axis([0 (tEnd - tStart) -1 1]);
plot(yPredicted(tStart:tEnd), 'Color', 'r','LineWidth',2);
plot(yActual(tStart:tEnd), 'Color', 'b','LineWidth',2);
xlabel('Frame','FontSize',15,'FontWeight','bold'),ylabel('Y','FontSize',15,'FontWeight','bold')
AX = legend('Estimates','Ground Truth','FontSize',20,'FontWeight','bold')
LEG = findobj(AX,'type','text');
set(LEG,'FontSize',15,'FontWeight','bold');
hold off;


% Error Distance over time Figure
xDistance = xPredicted - xActual;
yDistance = yPredicted - yActual;
distance = sqrt((xDistance .* xDistance) + (yDistance .* yDistance));

% figure
% hold on;
% axis([0 (tEnd - tStart) 0 1.1]);
% plot(distance(tStart:tEnd), 'Color', 'r','LineWidth',2);
% xlabel('Frame','FontSize',15,'FontWeight','bold');
% ylabel('Normalized Distance','FontSize',15,'FontWeight','bold');
% AX = legend('Distance', 'FontSize',20,'FontWeight','bold')
% LEG = findobj(AX,'type','text');
% set(LEG,'FontSize',15,'FontWeight','bold');
% hold off;

%Raw sensor values over time figure
colors = {[1.0 0 0] [0 1.0 0] [0 0 1.0] [1.0 0.5 0] [0.5 0 1.0] [0.0 0.5 0.5]};
for i = 1:6
 figure
 hold on;
 plot(sensors(tStart:tEnd, i), 'Color', colors{i}, 'LineWidth',1);
 axis([0 (tEnd - tStart) -1 1]);
 xlabel('Frame','FontSize',15,'FontWeight','bold');
 ylabel('Luminance','FontSize',15,'FontWeight','bold');
 if (i == 4)
     AX = legend(strcat('Sensor-', num2str(4)),'FontSize',20,'FontWeight','bold')
 elseif (i == 5)
     AX = legend(strcat('Sensor-', num2str(3)),'FontSize',20,'FontWeight','bold')
 else
    AX = legend(strcat('Sensor-', num2str(i-1)),'FontSize',20,'FontWeight','bold')
 end
 LEG = findobj(AX,'type','text');
 set(LEG,'FontSize',15,'FontWeight','bold');
 hold off;
end



