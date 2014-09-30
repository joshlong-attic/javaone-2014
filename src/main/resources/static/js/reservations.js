var appName = 'reservations';

function jsUrl(u) {
    return '/js/lib/' + u;
}


require.config({
    paths: {
        doge: 'doge',
        stomp: jsUrl('stomp-websocket/lib/stomp'),
        sockjs: jsUrl('sockjs/sockjs'),
        angular: jsUrl('angular/angular'),
        domReady: jsUrl('requirejs-domready/domReady')
    },
    shim: {
        angular: {
            exports: 'angular'
        }
    }
});

define([ 'require', 'angular' ], function (require, angular) {
    'use strict';
    require([ 'sockjs', 'angular', 'stomp', 'domReady!' ], function (sockjs, angular, stomp) {
        angular.bootstrap(document, [ appName ]);
    });

    var doge = angular.module(appName, []);

    doge.controller('ReservationController', [
        '$scope', '$http', '$log', function ($scope, $http, $log) {

            $scope.reservations = [];

            $scope.all = function () {
                $http.get('/reservations').success(function (data) {
                    $scope.reservations = data;
                });
            };

            require([ 'sockjs', 'stomp' ], function (sockjs, stomp) {
                var socket = new SockJS('/monitor');
                var client = Stomp.over(socket);
                client.connect({}, function (frame) {

                    console.log('connected...');

                    client.subscribe("/topic/alarms", function (message) {
                        var reservation = JSON.parse(message.body);
                        console.log('reservation=' + reservation);
                        $scope.all();
                    });

                }, function (error) {
                    console.log('STOMP protocol error ' + error);
                });
            });


        } ]);
});