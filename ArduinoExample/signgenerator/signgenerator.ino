void setup() {
  Serial.begin(115200);
  while (!Serial) {
    ;
  }
}
float sign1 = -1.0, sign2 = 1.0;
String sendString;
int upcount = 1;
int counter = 0;
void loop() {
  if (counter < 3) {
    delay(80);
    if ((int)sign1 <= 1 && upcount == 1) {
      if ((int)sign1 == 1) {
        upcount = 0;
      }
      else {
        sendString = makeSendString(sign1, sign2);
        Serial.println(sendString);
        sign1 += 0.01;
        sign2 -= 0.01;
      }
    }
    else if ((int)sign1 >= -1 && upcount == 0) {
      if ((int)sign1 == -1) {
        upcount = 1;
        counter += 1;
      }
      else {
        sendString = makeSendString(sign1, sign2);
        Serial.println(sendString);
        sign1 -= 0.01;
        sign2 += 0.01;
      }
    }
  }
}

String makeSendString (float a, float b) {
  String result;
  result = a;
  result += " ";
  result += b;
  return result;
}
