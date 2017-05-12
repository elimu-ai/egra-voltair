#ifndef TR_H
#define TR_H

#include <QObject>
#include <QMap>

class TR : public QObject
{
    Q_OBJECT
public:
    void loadDictionary(const QString &language);

    Q_INVOKABLE QString value(const QString &key) const;

    static TR* getInstance();
private:
    TR(QObject* parent = nullptr);
    void parseFileLine(const QString &line);


    static TR* sInstance;
    QMap<QString, QString> _dictionary;
    QString _currentLanguage;
};

#endif // TR_H
